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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiProcesadorServiceImpl implements ProcesadorIAService {

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

        // El audio no se guarda en ningún momento (RNF-02): solo vivió en
        // memoria durante este método, como bytes y luego como base64.
        return new AudioResponse(gastos, modelo.transcripcion());
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

        return iaRestClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(GeminiResponse.class);
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
