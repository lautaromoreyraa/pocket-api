package com.pocket.controller;

import com.pocket.domain.Categoria;
import com.pocket.repository.CategoriaRepository;
import com.pocket.repository.GastoRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GastoControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private GastoRepository gastoRepository;

    private Integer categoriaId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        // @Transactional revierte cada test, así que solo hace falta sembrar lo propio.
        categoriaId = categoriaRepository.save(Categoria.builder()
                .nombre("Supermercado").icono("cart").color("#000000").orden(1).build()).getId();
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

    private String cuerpoGasto(UUID idempotencyKey) {
        return """
                {
                  "idempotencyKey": "%s",
                  "monto": 1500.00,
                  "categoriaId": %d,
                  "descripcion": "super",
                  "medioPago": "EFECTIVO",
                  "fechaGasto": "2026-01-20"
                }
                """.formatted(idempotencyKey, categoriaId);
    }

    @Test
    @DisplayName("POST /api/gastos crea el gasto e imputa a enero")
    void altaManualImputaAEnero() throws Exception {
        mockMvc.perform(post("/api/gastos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoGasto(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaImputacion").value("2026-01-01"))
                .andExpect(jsonPath("$.esCuota").value(false));

        assertThat(gastoRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Dos altas con la misma idempotencyKey crean un solo gasto con el mismo id")
    void mismaIdempotencyKeyUnSoloGasto() throws Exception {
        UUID key = UUID.randomUUID();

        String primera = mockMvc.perform(post("/api/gastos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoGasto(key)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String segunda = mockMvc.perform(post("/api/gastos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoGasto(key)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String idPrimera = primera.split("\"id\":\"")[1].split("\"")[0];
        String idSegunda = segunda.split("\"id\":\"")[1].split("\"")[0];

        assertThat(idSegunda).isEqualTo(idPrimera);
        assertThat(gastoRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un gasto con CREDITO se imputa al mes siguiente")
    void creditoImputaAlMesSiguiente() throws Exception {
        String cuerpo = """
                {
                  "idempotencyKey": "%s",
                  "monto": 2000.00,
                  "categoriaId": %d,
                  "descripcion": "credito",
                  "medioPago": "CREDITO",
                  "fechaGasto": "2026-01-20"
                }
                """.formatted(UUID.randomUUID(), categoriaId);

        mockMvc.perform(post("/api/gastos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaImputacion").value("2026-02-01"));
    }

    @Test
    @DisplayName("Una categoría inexistente devuelve 404")
    void categoriaInexistenteDa404() throws Exception {
        String cuerpo = """
                {
                  "idempotencyKey": "%s",
                  "monto": 1500.00,
                  "categoriaId": 9999,
                  "descripcion": "x",
                  "medioPago": "EFECTIVO",
                  "fechaGasto": "2026-01-20"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/gastos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un usuario no puede eliminar un gasto de otro (404)")
    void noSePuedeEliminarGastoAjeno() throws Exception {
        String respuesta = mockMvc.perform(post("/api/gastos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoGasto(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idAjeno = respuesta.split("\"id\":\"")[1].split("\"")[0];

        String tokenOtro = obtenerToken("device-" + UUID.randomUUID());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/gastos/" + idAjeno)
                        .header("Authorization", "Bearer " + tokenOtro))
                .andExpect(status().isNotFound());

        assertThat(gastoRepository.count()).isEqualTo(1);
    }
}
