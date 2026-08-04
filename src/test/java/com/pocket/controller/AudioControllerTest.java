package com.pocket.controller;

import com.pocket.domain.Categoria;
import com.pocket.dto.ia.GeminiResponse;
import com.pocket.repository.CategoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AudioControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CategoriaRepository categoriaRepository;

    // Reemplaza el RestClient real de la app: nunca le pegamos a Gemini de verdad.
    @MockitoBean
    @Qualifier("iaRestClient")
    private RestClient iaRestClient;

    private RestClient.RequestBodyUriSpec bodyUriSpec;
    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;
    private String token;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        categoriaRepository.save(Categoria.builder()
                .nombre("Delivery/Restaurantes").icono("food").color("#111111").orden(1).build());
        categoriaRepository.save(Categoria.builder()
                .nombre(Categoria.OTROS).icono("other").color("#222222").orden(2).build());

        bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(iaRestClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        // any(Object.class), no any(): body() está sobrecargado y any() sin
        // tipo resuelve contra la sobrecarga más específica, no la real.
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        token = obtenerToken("device-" + UUID.randomUUID());
    }

    private String obtenerToken(String deviceUuid) throws Exception {
        String respuesta = mockMvc.perform(post("/api/auth/dispositivo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceUuid\":\"" + deviceUuid + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return respuesta.split("\"token\":\"")[1].split("\"")[0];
    }

    private void geminiResponde(String textoJson) {
        GeminiResponse.Part part = new GeminiResponse.Part(textoJson);
        GeminiResponse.Content content = new GeminiResponse.Content(List.of(part));
        GeminiResponse.Candidate candidate = new GeminiResponse.Candidate(content);
        when(responseSpec.body(GeminiResponse.class)).thenReturn(new GeminiResponse(List.of(candidate)));
    }

    @Test
    @DisplayName("Un archivo vacío devuelve 400")
    void archivoVacioDevuelve400() throws Exception {
        MockMultipartFile vacio = new MockMultipartFile("audio", "audio.mp3", "audio/mpeg", new byte[0]);

        mockMvc.perform(multipart("/api/audio").file(vacio).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Un archivo que no es de tipo audio devuelve 400")
    void tipoNoAudioDevuelve400() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("audio", "doc.pdf", "application/pdf", "x".getBytes());

        mockMvc.perform(multipart("/api/audio").file(pdf).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Un audio con dos gastos devuelve 200 con dos detectados")
    void audioConDosGastosDevuelve200ConDosDetectados() throws Exception {
        geminiResponde("""
                {
                  "transcripcion": "Gasté cinco lucas en delivery y mil en otra cosa",
                  "gastos": [
                    {"monto": 5000, "categoria": "Delivery/Restaurantes", "descripcion": "delivery", "medioPago": "EFECTIVO", "cantidadCuotas": null},
                    {"monto": 1000, "categoria": "Otros", "descripcion": "otra cosa", "medioPago": "EFECTIVO", "cantidadCuotas": null}
                  ]
                }
                """);
        MockMultipartFile audio = new MockMultipartFile("audio", "audio.mp3", "audio/mpeg", "contenido".getBytes());

        mockMvc.perform(multipart("/api/audio").file(audio).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectados.length()").value(2));
    }

    @Test
    @DisplayName("Un audio sin gastos detectables devuelve 422")
    void audioSinGastosDevuelve422() throws Exception {
        geminiResponde("""
                {"transcripcion": "no se entendió", "gastos": []}
                """);
        MockMultipartFile audio = new MockMultipartFile("audio", "audio.mp3", "audio/mpeg", "contenido".getBytes());

        mockMvc.perform(multipart("/api/audio").file(audio).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Sin token, devuelve 401")
    void sinTokenDevuelve401() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "audio.mp3", "audio/mpeg", "contenido".getBytes());

        mockMvc.perform(multipart("/api/audio").file(audio))
                .andExpect(status().isUnauthorized());
    }
}
