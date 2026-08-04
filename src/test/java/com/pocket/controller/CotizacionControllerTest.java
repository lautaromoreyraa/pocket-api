package com.pocket.controller;

import com.pocket.domain.Cotizacion;
import com.pocket.repository.CotizacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CotizacionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CotizacionRepository cotizacionRepository;

    // Reemplaza el RestClient real de la app: nunca le pegamos a la API de verdad.
    @MockitoBean
    @Qualifier("cotizacionRestClient")
    private RestClient cotizacionRestClient;

    private RestClient.RequestHeadersUriSpec uriSpec;
    private RestClient.RequestHeadersSpec requestSpec;
    private RestClient.ResponseSpec responseSpec;
    private String token;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        requestSpec = mock(RestClient.RequestHeadersSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(cotizacionRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(com.pocket.dto.cotizacion.CotizacionApiResponse.class))
                .thenThrow(new RestClientException("API caída (mock por defecto del test)"));

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

    @Test
    @DisplayName("GET /api/cotizacion/blue con cotización vigente en base devuelve 200")
    void conCotizacionVigenteDevuelve200() throws Exception {
        cotizacionRepository.save(Cotizacion.builder()
                .valorCompra(new BigDecimal("1200.00"))
                .valorVenta(new BigDecimal("1250.00"))
                .fechaActualizacion(Instant.now())
                .build());

        mockMvc.perform(get("/api/cotizacion/blue").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compra").value(1200.00))
                .andExpect(jsonPath("$.venta").value(1250.00))
                .andExpect(jsonPath("$.desdeCache").value(false));
    }

    @Test
    @DisplayName("GET /api/cotizacion/blue con cotización vencida y API caída devuelve la guardada con desdeCache=true")
    void conCotizacionVencidaYApiCaidaDevuelveDesdeCache() throws Exception {
        cotizacionRepository.save(Cotizacion.builder()
                .valorCompra(new BigDecimal("1200.00"))
                .valorVenta(new BigDecimal("1250.00"))
                .fechaActualizacion(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());

        mockMvc.perform(get("/api/cotizacion/blue").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compra").value(1200.00))
                .andExpect(jsonPath("$.desdeCache").value(true));
    }

    @Test
    @DisplayName("GET /api/cotizacion/blue sin ninguna cotización guardada y API caída devuelve 503")
    void sinCotizacionYApiCaidaDevuelve503() throws Exception {
        mockMvc.perform(get("/api/cotizacion/blue").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.estado").value(503));
    }
}
