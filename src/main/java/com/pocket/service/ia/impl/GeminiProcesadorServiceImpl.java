package com.pocket.service.ia.impl;

import tools.jackson.databind.ObjectMapper;
import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.domain.Usuario;
import com.pocket.dto.audio.AudioResponse;
import com.pocket.dto.ia.GeminiRequest;
import com.pocket.dto.ia.RespuestaModeloIA;
import com.pocket.dto.ia.GeminiResponse;
import com.pocket.exception.AudioNoComprendidoException;
import com.pocket.repository.CategoriaRepository;
import com.pocket.service.ia.ArmadorDeGastos;
import com.pocket.service.ia.ReintentosDeIA;
import com.pocket.service.ia.PromptDeExtraccion;
import com.pocket.service.ia.ProcesadorIAService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "pocket.ia.proveedor", havingValue = "gemini", matchIfMissing = true)
public class GeminiProcesadorServiceImpl implements ProcesadorIAService {

    private final RestClient iaRestClient;
    private final PocketProperties props;
    private final CategoriaRepository categoriaRepository;
    private final ObjectMapper objectMapper;
    private final ArmadorDeGastos armadorDeGastos;
    private final ReintentosDeIA reintentos;

    @Override
    public AudioResponse extraerGastos(MultipartFile audio, Usuario usuario) {
        List<Categoria> categorias = categoriaRepository.findAllByOrderByOrdenAsc();
        String prompt = PromptDeExtraccion.desdeAudio(categorias);
        String base64Audio = codificar(audio);

        GeminiResponse respuesta = llamarApi(prompt, base64Audio, audio.getContentType());
        RespuestaModeloIA modelo = parsear(extraerTexto(respuesta));

        return armadorDeGastos.armar(modelo, modelo.transcripcion(), categorias);
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
        GeminiRequest.GenerationConfig config = new GeminiRequest.GenerationConfig("application/json");
        GeminiRequest body = new GeminiRequest(List.of(contenido), config);

        String uri = UriComponentsBuilder.fromPath("/models/{modelo}:generateContent")
                .queryParam("key", props.getIa().getApiKey())
                .buildAndExpand(props.getIa().getModelo())
                .toUriString();

        return reintentos.ejecutar(() -> iaRestClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(GeminiResponse.class));
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

    private RespuestaModeloIA parsear(String texto) {
        try {
            return objectMapper.readValue(texto, RespuestaModeloIA.class);
        } catch (Exception e) {
            log.warn("No se pudo parsear la respuesta de Gemini como JSON: {}", e.getMessage());
            throw new AudioNoComprendidoException("No se pudo interpretar la respuesta de la IA");
        }
    }
}
