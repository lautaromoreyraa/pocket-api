package com.pocket.controller;

import com.pocket.domain.Categoria;
import com.pocket.domain.Gasto;
import com.pocket.repository.CategoriaRepository;
import com.pocket.repository.CompraFinanciadaRepository;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CompraFinanciadaControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private CompraFinanciadaRepository compraRepository;
    @Autowired private GastoRepository gastoRepository;

    private Integer hogarId;
    private Integer entretenimientoId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        // @Transactional revierte cada test, así que solo hace falta sembrar lo propio.
        hogarId = categoriaRepository.save(Categoria.builder()
                .nombre("Hogar").icono("home").color("#111111").orden(1).build()).getId();
        entretenimientoId = categoriaRepository.save(Categoria.builder()
                .nombre("Entretenimiento").icono("game").color("#222222").orden(2).build()).getId();
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

    private String cuerpoCompra(UUID key, Integer categoriaId) {
        return """
                {
                  "idempotencyKey": "%s",
                  "montoTotal": 12500.00,
                  "cantidadCuotas": 9,
                  "categoriaId": %d,
                  "descripcion": "TV",
                  "fechaCompra": "2026-01-15"
                }
                """.formatted(key, categoriaId);
    }

    private String crearCompra(UUID key) throws Exception {
        String respuesta = mockMvc.perform(post("/api/compras")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCompra(key, hogarId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return respuesta.split("\"id\":\"")[1].split("\"")[0];
    }

    private BigDecimal sumaDeCuotas() {
        return gastoRepository.findAll().stream()
                .map(Gasto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("POST crea 9 cuotas que suman exactamente $12.500")
    void altaGeneraCuotasQueSumanElTotal() throws Exception {
        crearCompra(UUID.randomUUID());

        assertThat(gastoRepository.count()).isEqualTo(9);
        assertThat(sumaDeCuotas()).isEqualByComparingTo(new BigDecimal("12500.00"));
    }

    @Test
    @DisplayName("PUT cambia la categoría de las 9 cuotas sin tocar los montos")
    void editarPropagaCategoria() throws Exception {
        String compraId = crearCompra(UUID.randomUUID());

        mockMvc.perform(put("/api/compras/" + compraId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoriaId\": %d, \"descripcion\": \"Smart TV\"}".formatted(entretenimientoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("Entretenimiento"))
                // Las 9 cuotas de la respuesta quedaron con la nueva categoría.
                .andExpect(jsonPath("$.cuotas.length()").value(9))
                .andExpect(jsonPath("$.cuotas[*].categoria", everyItem(is("Entretenimiento"))));

        // Los montos no se tocaron: la suma sigue cerrando exacta.
        assertThat(sumaDeCuotas()).isEqualByComparingTo(new BigDecimal("12500.00"));
    }

    @Test
    @DisplayName("DELETE borra la compra y sus 9 cuotas")
    void eliminarBorraLasCuotas() throws Exception {
        String compraId = crearCompra(UUID.randomUUID());
        assertThat(gastoRepository.count()).isEqualTo(9);

        mockMvc.perform(delete("/api/compras/" + compraId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(compraRepository.count()).isZero();
        assertThat(gastoRepository.count()).isZero();
    }

    @Test
    @DisplayName("Dos POST con la misma idempotencyKey crean una sola compra")
    void mismaIdempotencyKeyUnaSolaCompra() throws Exception {
        UUID key = UUID.randomUUID();
        String id1 = crearCompra(key);
        String id2 = crearCompra(key);

        assertThat(id2).isEqualTo(id1);
        assertThat(compraRepository.count()).isEqualTo(1);
        assertThat(gastoRepository.count()).isEqualTo(9);
    }

    @Test
    @DisplayName("Una compra de otro usuario devuelve 404")
    void compraAjenaDa404() throws Exception {
        String compraId = crearCompra(UUID.randomUUID());
        String tokenOtro = obtenerToken("device-" + UUID.randomUUID());

        mockMvc.perform(delete("/api/compras/" + compraId)
                        .header("Authorization", "Bearer " + tokenOtro))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/compras/" + compraId)
                        .header("Authorization", "Bearer " + tokenOtro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoriaId\": %d}".formatted(entretenimientoId)))
                .andExpect(status().isNotFound());

        assertThat(compraRepository.count()).isEqualTo(1);
    }
}
