package com.pocket.service.ia.impl;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.domain.Usuario;
import com.pocket.dto.audio.AudioResponse;
import com.pocket.dto.ia.GroqChatRequest;
import com.pocket.dto.ia.GroqChatResponse;
import com.pocket.dto.ia.GroqTranscripcionResponse;
import com.pocket.dto.ia.RespuestaModeloIA;
import com.pocket.exception.AudioNoComprendidoException;
import com.pocket.repository.CategoriaRepository;
import com.pocket.service.ia.ArmadorDeGastos;
import com.pocket.service.ia.ProcesadorIAService;
import com.pocket.service.ia.PromptDeExtraccion;
import com.pocket.service.ia.ReintentosDeIA;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * Extracción de gastos en dos pasos: primero se transcribe el audio, después se
 * leen los gastos de esa transcripción.
 *
 * <pre>
 *   audio ── modelo de voz ──▶ transcripción ── modelo de texto ──▶ gastos
 * </pre>
 *
 * La diferencia con el proveedor multimodal no es la cuota: es <b>quién escribe
 * la transcripción</b>. Con un solo modelo que escucha y responde, la
 * transcripción contra la que se verifican los montos la redacta el mismo
 * modelo que podría estar inventándolos; si fabrica un gasto, puede fabricar la
 * frase que lo justifica.
 *
 * Acá el modelo que extrae <b>nunca recibe el audio</b>. Solo ve el texto que
 * produjo un modelo dedicado a transcribir, así que un monto que no esté escrito
 * no tiene de dónde salir, y si aun así lo inventa, no aparece en la
 * transcripción y {@link ArmadorDeGastos} lo descarta. La verificación pasa de
 * ser un modelo contra sí mismo a dos fuentes independientes.
 *
 * Se activa con {@code pocket.ia.proveedor=groq}. Ver "Gastos fabricados" en el
 * CLAUDE.md.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pocket.ia.proveedor", havingValue = "groq")
public class GroqProcesadorServiceImpl implements ProcesadorIAService {

    private final RestClient groqRestClient;
    private final PocketProperties props;
    private final CategoriaRepository categoriaRepository;
    private final ObjectMapper objectMapper;
    private final ArmadorDeGastos armadorDeGastos;
    private final ReintentosDeIA reintentos;

    @Override
    public AudioResponse extraerGastos(MultipartFile audio, Usuario usuario) {
        String transcripcion = transcribir(audio);

        if (transcripcion == null || transcripcion.isBlank()) {
            throw new AudioNoComprendidoException(
                    "No se entendió nada del audio. Podés reintentar o cargarlo a mano.");
        }
        log.debug("Transcripción recibida: '{}'", transcripcion);

        List<Categoria> categorias = categoriaRepository.findAllByOrderByOrdenAsc();
        RespuestaModeloIA modelo = extraerDelTexto(transcripcion, categorias);

        // La transcripción que se verifica y la que se devuelve es la del modelo
        // de voz, no algo que el de texto pueda haber reescrito: es la evidencia
        // y tiene que venir de quien efectivamente escuchó.
        //
        // El audio no se guarda en ningún momento (RNF-02): solo vivió en
        // memoria durante este método.
        return armadorDeGastos.armar(modelo, transcripcion, categorias);
    }

    /** Paso 1 — el audio se manda tal cual, sin base64: el endpoint es multipart. */
    private String transcribir(MultipartFile audio) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", recurso(audio));
        form.add("model", props.getIa().getGroq().getModeloVoz());
        // Fijar el idioma mejora bastante la transcripción de jerga rioplatense
        // ("lucas", "gambas") frente a dejar que el modelo lo adivine.
        form.add("language", "es");
        form.add("response_format", "json");

        GroqTranscripcionResponse respuesta = reintentos.ejecutar(() -> groqRestClient.post()
                .uri("/audio/transcriptions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getIa().getGroq().getApiKey())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(GroqTranscripcionResponse.class));

        return respuesta == null ? null : respuesta.text();
    }

    /** Paso 2 — solo texto de entrada y JSON de salida. Este modelo no oye nada. */
    private RespuestaModeloIA extraerDelTexto(String transcripcion, List<Categoria> categorias) {
        String prompt = PromptDeExtraccion.desdeTranscripcion(categorias, transcripcion);

        GroqChatResponse respuesta = reintentos.ejecutar(() -> groqRestClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getIa().getGroq().getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(GroqChatRequest.deTexto(props.getIa().getGroq().getModeloTexto(), prompt))
                .retrieve()
                .body(GroqChatResponse.class));

        return parsear(extraerContenido(respuesta));
    }

    /**
     * El nombre del archivo importa: el endpoint lo usa para deducir el formato
     * del audio, y sin él rechaza el request.
     */
    private Resource recurso(MultipartFile audio) {
        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo de audio", e);
        }

        String nombre = audio.getOriginalFilename() == null
                ? "audio.m4a" : audio.getOriginalFilename();

        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return nombre;
            }
        };
    }

    private String extraerContenido(GroqChatResponse respuesta) {
        if (respuesta == null || respuesta.choices() == null || respuesta.choices().isEmpty()) {
            throw new AudioNoComprendidoException("La IA no devolvió ninguna respuesta interpretable");
        }
        GroqChatResponse.Mensaje mensaje = respuesta.choices().get(0).message();
        if (mensaje == null || mensaje.content() == null) {
            throw new AudioNoComprendidoException("La IA no devolvió ninguna respuesta interpretable");
        }
        return mensaje.content();
    }

    private RespuestaModeloIA parsear(String texto) {
        try {
            return objectMapper.readValue(texto, RespuestaModeloIA.class);
        } catch (Exception e) {
            log.warn("No se pudo parsear la respuesta del modelo: {}", texto, e);
            throw new AudioNoComprendidoException("No se pudo interpretar la respuesta de la IA");
        }
    }
}
