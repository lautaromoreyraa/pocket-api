package com.pocket.service.ia.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.domain.Usuario;
import com.pocket.dto.audio.AudioResponse;
import com.pocket.dto.audio.GastoDetectado;
import com.pocket.dto.ia.GeminiRequest;
import com.pocket.dto.ia.GeminiRespuestaModelo;
import com.pocket.dto.ia.GeminiResponse;
import com.pocket.enumeration.MedioPago;
import com.pocket.exception.AudioNoComprendidoException;
import com.pocket.repository.CategoriaRepository;
import com.pocket.service.ia.GeminiPrompt;
import com.pocket.service.ia.ProcesadorIAService;
import com.pocket.util.MontosEnTranscripcion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiProcesadorServiceImpl implements ProcesadorIAService {

    /** `"retryDelay": "35s"` dentro del RetryInfo que Gemini manda en el 429. */
    private static final Pattern RETRY_DELAY =
            Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+(?:\\.\\d+)?)s\"");

    private final RestClient iaRestClient;
    private final PocketProperties props;
    private final CategoriaRepository categoriaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public AudioResponse extraerGastos(MultipartFile audio, Usuario usuario) {
        List<Categoria> categorias = categoriaRepository.findAllByOrderByOrdenAsc();
        String prompt = GeminiPrompt.construir(categorias);
        String base64Audio = codificar(audio);

        GeminiResponse respuesta = llamarApi(prompt, base64Audio, audio.getContentType());
        String texto = extraerTexto(respuesta);
        GeminiRespuestaModelo modelo = parsear(texto);

        Map<String, Categoria> categoriasPorNombre = categorias.stream()
                .collect(Collectors.toMap(c -> normalizar(c.getNombre()), c -> c, (a, b) -> a));
        Categoria otros = categoriasPorNombre.get(normalizar(Categoria.OTROS));

        LocalDate hoy = LocalDate.now(ZoneId.of(props.getPeriodo().getZonaHoraria()));

        List<GastoDetectado> gastos = modelo.gastos() == null ? List.of() : modelo.gastos().stream()
                .filter(g -> g.monto() != null && g.monto().compareTo(BigDecimal.ZERO) > 0)
                .map(g -> aGastoDetectado(g, categoriasPorNombre, otros, hoy))
                .toList();

        if (gastos.isEmpty()) {
            throw new AudioNoComprendidoException(
                    "No se detectó ningún gasto en el audio. Podés reintentar o cargarlo a mano.");
        }

        verificarCoherencia(gastos, modelo.transcripcion());

        // El audio no se guarda en ningún momento (RNF-02): solo vivió en
        // memoria durante este método, como bytes y luego como base64.
        return new AudioResponse(gastos, modelo.transcripcion());
    }

    /**
     * Todo monto detectado tiene que aparecer en la transcripción, en dígitos o
     * en letras. Si no aparece, el modelo lo fabricó.
     *
     * No es paranoia: un modelo que no puede decodificar el audio igual devuelve
     * un JSON bien formado, con montos y categorías verosímiles, en vez de
     * fallar. Sin este control eso llega al usuario como un 200 indistinguible
     * de una detección real.
     *
     * El criterio es deliberadamente estricto: alcanza con que un solo monto no
     * cierre para descartar la respuesta entera. Un 422 de más solo cuesta un
     * reintento o una carga manual; un gasto inventado con un monto plausible no
     * hay forma de que el usuario lo detecte.
     */
    private void verificarCoherencia(List<GastoDetectado> gastos, String transcripcion) {
        List<BigDecimal> fabricados = gastos.stream()
                .map(GastoDetectado::monto)
                .filter(monto -> !MontosEnTranscripcion.menciona(transcripcion, monto))
                .toList();

        if (!fabricados.isEmpty()) {
            log.warn("Respuesta descartada por incoherente: los montos {} no aparecen en la "
                            + "transcripción '{}'. Modelo: {}",
                    fabricados, transcripcion, props.getIa().getModelo());
            throw new AudioNoComprendidoException(
                    "No se pudo verificar lo que dice el audio. Podés reintentar o cargarlo a mano.");
        }
    }

    private GastoDetectado aGastoDetectado(GeminiRespuestaModelo.GastoModelo detectado,
                                            Map<String, Categoria> categoriasPorNombre,
                                            Categoria otros, LocalDate hoy) {
        Categoria categoria = detectado.categoria() == null
                ? null : categoriasPorNombre.get(normalizar(detectado.categoria()));
        if (categoria == null) {
            categoria = otros;
        }

        return new GastoDetectado(
                UUID.randomUUID(),
                detectado.monto(),
                categoria.getId(),
                detectado.descripcion(),
                resolverMedioPago(detectado.medioPago()),
                hoy,
                detectado.cantidadCuotas());
    }

    /** Medio de pago no reconocido o ausente -> EFECTIVO, el más común. */
    private MedioPago resolverMedioPago(String texto) {
        if (texto == null) {
            return MedioPago.EFECTIVO;
        }
        try {
            return MedioPago.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MedioPago.EFECTIVO;
        }
    }

    private String normalizar(String nombre) {
        return nombre.trim().toLowerCase(Locale.ROOT);
    }

    private String codificar(MultipartFile audio) {
        try {
            return Base64.getEncoder().encodeToString(audio.getBytes());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo de audio", e);
        }
    }

    private GeminiResponse llamarApi(String prompt, String base64Audio, String mimeType) {
        GeminiRequest.Part textoParte = new GeminiRequest.Part(prompt, null);
        GeminiRequest.Part audioParte =
                new GeminiRequest.Part(null, new GeminiRequest.InlineData(mimeType, base64Audio));
        GeminiRequest.Content contenido = new GeminiRequest.Content(List.of(textoParte, audioParte));
        // Pedimos JSON puro también a nivel de API, además de limpiar
        // backticks a mano: doble resguardo contra que el modelo envuelva
        // la respuesta en markdown.
        GeminiRequest.GenerationConfig config = new GeminiRequest.GenerationConfig("application/json");
        GeminiRequest body = new GeminiRequest(List.of(contenido), config);

        String uri = UriComponentsBuilder.fromPath("/models/{modelo}:generateContent")
                .queryParam("key", props.getIa().getApiKey())
                .buildAndExpand(props.getIa().getModelo())
                .toUriString();

        return conReintentos(() -> iaRestClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(GeminiResponse.class));
    }

    /**
     * Reintenta con backoff exponencial mientras la falla sea transitoria.
     *
     * Gemini devuelve 503 ("high demand") y 429 seguido, y el tiempo de
     * respuesta del mismo audio varía entre 9s y 30s: un solo intento convierte
     * cualquiera de esas dos cosas en un error para el usuario.
     */
    private GeminiResponse conReintentos(Supplier<GeminiResponse> llamada) {
        int intentos = Math.max(1, props.getIa().getIntentos());
        long techo = props.getIa().getEsperaMaximaMs();
        long backoff = props.getIa().getBackoffInicialMs();

        for (int intento = 1; ; intento++) {
            try {
                return llamada.get();
            } catch (RestClientException e) {
                if (intento >= intentos || !esTransitorio(e)) {
                    throw e;
                }

                // Cuando el proveedor dice cuánto esperar, esperar menos es
                // pedir el mismo error de nuevo: el backoff exponencial solo
                // aplica si no hay indicación.
                Long pedida = esperaPedida(e);
                long espera = pedida == null ? backoff : Math.max(pedida, backoff);

                if (espera > techo) {
                    log.warn("Intento {}/{} falló y el proveedor pide esperar {}ms, más que el "
                                    + "techo de {}ms. No se reintenta.",
                            intento, intentos, espera, techo);
                    throw e;
                }

                log.warn("Intento {}/{} falló ({}). Reintento en {}ms{}",
                        intento, intentos, e.getMessage(), espera,
                        pedida == null ? "" : " (pedido por el proveedor)");
                dormir(espera);
                backoff *= 2;
            }
        }
    }

    /**
     * Cuánto pide esperar el proveedor, en milisegundos, o null si no lo dice.
     *
     * Gemini lo manda en el cuerpo del 429, dentro de `error.details[]`, en una
     * entrada de tipo `RetryInfo` con un `retryDelay` como "35s" o "1.5s". El
     * header `Retry-After` es el estándar y se mira primero, pero Gemini no
     * siempre lo incluye.
     */
    private Long esperaPedida(RestClientException e) {
        if (!(e instanceof HttpStatusCodeException http)) {
            return null;
        }

        String retryAfter = http.getResponseHeaders() == null
                ? null : http.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                return Long.parseLong(retryAfter.trim()) * 1000;
            } catch (NumberFormatException ignorado) {
                // Retry-After admite también una fecha HTTP. No vale la pena
                // parsearla: el cuerpo de Gemini es la fuente principal.
            }
        }

        Matcher m = RETRY_DELAY.matcher(http.getResponseBodyAsString());
        if (!m.find()) {
            return null;
        }
        return Math.round(Double.parseDouble(m.group(1)) * 1000);
    }

    /**
     * Transitorio es lo que puede salir distinto si se vuelve a preguntar:
     * saturación del proveedor (503), rate limit (429) y timeouts o cortes de
     * red. Un 400 por request mal armado o un 404 de modelo inexistente no
     * mejoran esperando, y reintentarlos solo multiplica la espera del usuario.
     */
    private boolean esTransitorio(RestClientException e) {
        if (e instanceof HttpServerErrorException) {
            return true;
        }
        if (e instanceof HttpClientErrorException clientError) {
            return clientError.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
        }
        return e instanceof ResourceAccessException;
    }

    private void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reintento interrumpido", e);
        }
    }

    private String extraerTexto(GeminiResponse respuesta) {
        String texto = respuesta == null || respuesta.candidates() == null || respuesta.candidates().isEmpty()
                ? null
                : primerTexto(respuesta);
        if (texto == null || texto.isBlank()) {
            throw new AudioNoComprendidoException("La IA no devolvió ninguna respuesta interpretable");
        }
        return limpiarMarkdown(texto);
    }

    private String primerTexto(GeminiResponse respuesta) {
        GeminiResponse.Content contenido = respuesta.candidates().get(0).content();
        if (contenido == null || contenido.parts() == null || contenido.parts().isEmpty()) {
            return null;
        }
        return contenido.parts().get(0).text();
    }

    private String limpiarMarkdown(String texto) {
        String limpio = texto.trim();
        if (limpio.startsWith("```")) {
            limpio = limpio.replaceFirst("^```[a-zA-Z]*\\s*", "");
            limpio = limpio.replaceFirst("```\\s*$", "");
        }
        return limpio.trim();
    }

    private GeminiRespuestaModelo parsear(String texto) {
        try {
            return objectMapper.readValue(texto, GeminiRespuestaModelo.class);
        } catch (Exception e) {
            log.warn("No se pudo parsear la respuesta de Gemini como JSON: {}", e.getMessage());
            throw new AudioNoComprendidoException("No se pudo interpretar la respuesta de la IA");
        }
    }
}
