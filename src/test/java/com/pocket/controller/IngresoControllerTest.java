package com.pocket.controller;

import com.pocket.repository.IngresoRepository;
import com.pocket.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IngresoControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private IngresoRepository ingresoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private String token;
    private UUID usuarioId;

    @BeforeEach
    void setUp() throws Exception {
        // @Transactional revierte cada test, así que solo hace falta identificar al usuario.
        String deviceUuid = "device-" + UUID.randomUUID();
        token = obtenerToken(deviceUuid);
        usuarioId = usuarioRepository.findByDeviceUuid(deviceUuid).orElseThrow().getId();
    }

    private String obtenerToken(String deviceUuid) throws Exception {
        String respuesta = mockMvc.perform(post("/api/auth/dispositivo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceUuid\":\"" + deviceUuid + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return respuesta.split("\"token\":\"")[1].split("\"")[0];
    }

    private String cuerpo(String monto, String fecha) {
        return """
                {
                  "monto": %s,
                  "descripcion": "sueldo",
                  "periodo": "%s"
                }
                """.formatted(monto, fecha);
    }

    private void crearIngreso(String token, String monto, String fecha) throws Exception {
        mockMvc.perform(post("/api/ingresos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(monto, fecha)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST con período \"2026-03\" guarda el ingreso con fecha 01/03")
    void altaNormalizaElPeriodo() throws Exception {
        mockMvc.perform(post("/api/ingresos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("500000.00", "2026-03")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodo").value("2026-03-01"));
    }

    @Test
    @DisplayName("Un período con día se rechaza con 400: el ingreso se carga al mes")
    void periodoConDiaSeRechaza() throws Exception {
        mockMvc.perform(post("/api/ingresos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("500000.00", "2026-03-15")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Varios ingresos en el mismo mes se listan todos")
    void listaTodosLosDelMes() throws Exception {
        crearIngreso(token, "500000.00", "2026-03");
        crearIngreso(token, "120000.00", "2026-03");

        mockMvc.perform(get("/api/ingresos?periodo=2026-03")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("totalDelPeriodo suma los ingresos del mes (base de la capacidad de ahorro, RN-06)")
    void totalDelPeriodoSumaLosIngresos() throws Exception {
        crearIngreso(token, "500000.00", "2026-03");
        crearIngreso(token, "120000.00", "2026-03");

        BigDecimal total = ingresoRepository.totalDelPeriodo(usuarioId, LocalDate.of(2026, 3, 1));

        assertThat(total).isEqualByComparingTo(new BigDecimal("620000.00"));
    }

    @Test
    @DisplayName("totalDelPeriodo sin ingresos devuelve cero, no null (RF-33)")
    void totalDelPeriodoSinIngresosEsCero() {
        // Sin ingresos cargados no se muestra la capacidad de ahorro: el balance
        // suma este total, así que un null acá sería un NPE en el cálculo.
        BigDecimal total = ingresoRepository.totalDelPeriodo(usuarioId, LocalDate.of(2026, 3, 1));

        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Un ingreso de otro usuario devuelve 404 al eliminarlo")
    void ingresoAjenoDa404() throws Exception {
        String respuesta = mockMvc.perform(post("/api/ingresos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("500000.00", "2026-03")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idAjeno = respuesta.split("\"id\":\"")[1].split("\"")[0];

        String tokenOtro = obtenerToken("device-" + UUID.randomUUID());

        mockMvc.perform(delete("/api/ingresos/" + idAjeno)
                        .header("Authorization", "Bearer " + tokenOtro))
                .andExpect(status().isNotFound());

        assertThat(ingresoRepository.count()).isEqualTo(1);
    }
}
