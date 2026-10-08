package com.pocket.controller;

import com.pocket.domain.Categoria;
import com.pocket.dto.ia.GeminiResponse;
import com.pocket.repository.CategoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MockMvc no pasa por el servlet container real: no ejercita Tomcat ni el
 * dispatch interno a /error que causó el 401 fantasma con audios grandes
 * (ver SecurityConfig y JwtAuthenticationFilter). Estos tests usan HTTP
 * real contra un puerto real para poder detectar ese tipo de problema.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AudioControllerRealHttpTest {

    private static final int TAMANIO_AUDIO_REALISTA = 100_000; // ~100 KB

    @LocalServerPort
    private int port;

    @Autowired
    private CategoriaRepository categoriaRepository;

    // Reemplaza el RestClient real de la app: nunca le pegamos a Gemini de verdad.
    @MockitoBean
    @Qualifier("iaRestClient")
    private RestClient iaRestClient;

    private RestClient.RequestBodyUriSpec bodyUriSpec;
    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private String token;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        // Sin @Transactional a propósito: el request real corre en el hilo
        // del connector de Tomcat, no en el hilo del test, así que no ve los
        // datos de una transacción de test todavía sin commitear.
        categoriaRepository.deleteAll();
        categoriaRepository.save(Categoria.builder()
                .nombre(Categoria.OTROS).icono("other").color("#222222").orden(1).build());

        bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);
        when(iaRestClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        token = obtenerToken();
    }

    private String obtenerToken() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/auth/dispositivo"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"deviceUuid\":\"device-" + UUID.randomUUID() + "\"}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body().split("\"token\":\"")[1].split("\"")[0];
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private void geminiResponde(String textoJson) {
        GeminiResponse.Part part = new GeminiResponse.Part(textoJson);
        GeminiResponse.Content content = new GeminiResponse.Content(List.of(part));
        GeminiResponse.Candidate candidate = new GeminiResponse.Candidate(content);
        when(responseSpec.body(GeminiResponse.class)).thenReturn(new GeminiResponse(List.of(candidate)));
    }

    private void geminiFalla() {
        when(responseSpec.body(GeminiResponse.class))
                .thenThrow(new RestClientException("La API de Gemini no respondió"));
    }

    /** Cualquier excepción sin handler propio en GlobalExceptionHandler. */
    private void geminiFallaConExcepcionSinHandlerPropio() {
        when(responseSpec.body(GeminiResponse.class))
                .thenThrow(new IllegalStateException("simulando un fallo interno sin manejo específico"));
    }

    private HttpResponse<String> postAudio(int tamanioBytes) throws Exception {
        byte[] contenido = new byte[tamanioBytes];
        new Random().nextBytes(contenido);
        String boundary = "----boundary" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"audio\"; filename=\"grabacion.m4a\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes());
        out.write(contenido);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes());

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/audio"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("Un audio real de ~100 KB llega al controller y se procesa (no se cae por tamaño)")
    void audioDe100KbLlegaAlControllerYSeProcesa() throws Exception {
        geminiResponde("""
                {
                  "transcripcion": "Gasté mil pesos",
                  "gastos": [
                    {"monto": 1000, "categoria": "Otros", "descripcion": "algo", "medioPago": "EFECTIVO", "cantidadCuotas": null}
                  ]
                }
                """);

        HttpResponse<String> resp = postAudio(TAMANIO_AUDIO_REALISTA);

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).contains("\"detectados\"");
    }

    @Test
    @DisplayName("Si falla la llamada a Gemini, devuelve 502 con body claro (no un 401 fantasma)")
    void fallaDeGeminiDevuelve502ConBodyClaro() throws Exception {
        geminiFalla();

        HttpResponse<String> resp = postAudio(TAMANIO_AUDIO_REALISTA);

        assertThat(resp.statusCode()).isEqualTo(502);
        assertThat(resp.body()).isNotBlank();
        assertThat(resp.body()).doesNotContain("No autenticado");
    }

    @Test
    @DisplayName("Regresión: cualquier excepción sin handler propio no se enmascara como 401 fantasma")
    void excepcionSinHandlerPropioNoSeEnmascaraComo401() throws Exception {
        geminiFallaConExcepcionSinHandlerPropio();

        HttpResponse<String> resp = postAudio(TAMANIO_AUDIO_REALISTA);

        // Antes del fix (SecurityConfig sin /error en permitAll), esto daba
        // 401 con Content-Length: 0: Spring Security bloqueaba el forward
        // interno a /error porque JwtAuthenticationFilter, como todo
        // OncePerRequestFilter, no corre en dispatches de tipo ERROR por
        // default, así que el SecurityContext llegaba vacío a ese forward y
        // AuthorizationFilter lo rechazaba como no autenticado.
        assertThat(resp.statusCode()).isEqualTo(500);
        assertThat(resp.body()).isNotBlank();
        assertThat(resp.body()).doesNotContain("No autenticado");
    }

    @Test
    @DisplayName("Un archivo chico inválido sigue devolviendo 400 (no se rompió el caso que ya andaba)")
    void archivoChicoInvalidoDevuelve400() throws Exception {
        String boundary = "----boundary" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"audio\"; filename=\"notas.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\n").getBytes());
        out.write("hola".getBytes());
        out.write(("\r\n--" + boundary + "--\r\n").getBytes());

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/audio"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(resp.statusCode()).isEqualTo(400);
    }
}
