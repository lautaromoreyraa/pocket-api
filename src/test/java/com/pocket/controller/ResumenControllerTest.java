package com.pocket.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocket.domain.Categoria;
import com.pocket.repository.CategoriaRepository;
import com.pocket.repository.GastoRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ResumenControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private GastoRepository gastoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Integer deliveryId;
    private Integer hogarId;
    private String token;
    private UUID usuarioId;

    @BeforeEach
    void setUp() throws Exception {
        // @Transactional revierte cada test, así que solo hace falta sembrar lo propio.
        deliveryId = categoriaRepository.save(Categoria.builder()
                .nombre("Delivery/Restaurantes").icono("food").color("#111111").orden(1).build()).getId();
        hogarId = categoriaRepository.save(Categoria.builder()
                .nombre("Hogar").icono("home").color("#222222").orden(2).build()).getId();
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

    private void crearGasto(Integer categoriaId, String monto, String medioPago, String fechaGasto) throws Exception {
        String cuerpo = """
                {
                  "idempotencyKey": "%s",
                  "monto": %s,
                  "categoriaId": %d,
                  "descripcion": "x",
                  "medioPago": "%s",
                  "fechaGasto": "%s"
                }
                """.formatted(UUID.randomUUID(), monto, categoriaId, medioPago, fechaGasto);

        mockMvc.perform(post("/api/gastos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated());
    }

    private void crearCompra(Integer categoriaId, String montoTotal, int cantidadCuotas, String fechaCompra) throws Exception {
        String cuerpo = """
                {
                  "idempotencyKey": "%s",
                  "montoTotal": %s,
                  "cantidadCuotas": %d,
                  "categoriaId": %d,
                  "descripcion": "x",
                  "fechaCompra": "%s"
                }
                """.formatted(UUID.randomUUID(), montoTotal, cantidadCuotas, categoriaId, fechaCompra);

        mockMvc.perform(post("/api/compras")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated());
    }

    private void crearIngreso(String monto, String fecha) throws Exception {
        String cuerpo = """
                {
                  "monto": %s,
                  "descripcion": "sueldo",
                  "periodo": "%s"
                }
                """.formatted(monto, fecha);

        mockMvc.perform(post("/api/ingresos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated());
    }

    private JsonNode resumen(String periodo, boolean credito) throws Exception {
        String respuesta = mockMvc.perform(get("/api/resumen?periodo=" + periodo + "&credito=" + credito)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta);
    }

    private JsonNode categoria(JsonNode resumen, String nombre) {
        for (JsonNode c : resumen.get("porCategoria")) {
            if (c.get("nombre").asText().equals(nombre)) return c;
        }
        throw new AssertionError("No aparece la categoría " + nombre + " en porCategoria: " + resumen.get("porCategoria"));
    }

    @Test
    @DisplayName("Una categoría con 3 gastos en el mes aparece como hormiga; con 2 no")
    void tresOcurrenciasEsHormigaDosNo() throws Exception {
        crearGasto(deliveryId, "1000.00", "EFECTIVO", "2026-03-05");
        crearGasto(deliveryId, "1200.00", "EFECTIVO", "2026-03-10");
        crearGasto(deliveryId, "900.00", "EFECTIVO", "2026-03-20");
        crearGasto(hogarId, "5000.00", "EFECTIVO", "2026-03-01");
        crearGasto(hogarId, "3000.00", "EFECTIVO", "2026-03-15");

        JsonNode resumen = resumen("2026-03", false);

        assertThat(categoria(resumen, "Delivery/Restaurantes").get("ocurrencias").asLong()).isEqualTo(3);
        assertThat(categoria(resumen, "Delivery/Restaurantes").get("hormiga").asBoolean()).isTrue();
        assertThat(categoria(resumen, "Hogar").get("ocurrencias").asLong()).isEqualTo(2);
        assertThat(categoria(resumen, "Hogar").get("hormiga").asBoolean()).isFalse();

        assertThat(resumen.get("hormigas")).hasSize(1);
        assertThat(resumen.get("hormigas").get(0).get("categoria").asText()).isEqualTo("Delivery/Restaurantes");
        assertThat(resumen.get("hormigas").get(0).get("variacionVsPromedio").isNull()).isTrue();
    }

    @Test
    @DisplayName("Una compra en cuotas no cuenta para el umbral de hormiga, aunque sume ocurrencias en el gráfico (RN-02)")
    void cuotaNoCuentaComoHormiga() throws Exception {
        // La compra deja 1 cuota en marzo (comprada en febrero, imputada al mes siguiente).
        crearCompra(deliveryId, "6000.00", 6, "2026-02-15");
        // Dos gastos de crédito sueltos (no son cuotas) en la misma categoría y mes.
        crearGasto(deliveryId, "1000.00", "CREDITO", "2026-02-20");
        crearGasto(deliveryId, "1200.00", "CREDITO", "2026-02-25");

        JsonNode resumen = resumen("2026-03", true);

        // El gráfico ve las 3 filas (2 gastos sueltos + 1 cuota): las cuotas cuentan
        // para el total y el conteo mostrado (RF-23).
        assertThat(categoria(resumen, "Delivery/Restaurantes").get("ocurrencias").asLong()).isEqualTo(3);
        // Pero la lista de hormigas (RN-01/RN-02) excluye la cuota: solo 2 ocurrencias
        // reales, por debajo del umbral (3), así que la categoría no aparece.
        assertThat(resumen.get("hormigas")).isEmpty();
    }

    @Test
    @DisplayName("La pestaña débito no incluye gastos con CREDITO, y viceversa")
    void debitoYCreditoNoSeMezclan() throws Exception {
        crearGasto(deliveryId, "1000.00", "EFECTIVO", "2026-03-05");
        crearGasto(deliveryId, "2000.00", "CREDITO", "2026-02-10");

        mockMvc.perform(get("/api/resumen?periodo=2026-03&credito=false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.total").value(1000.00));

        mockMvc.perform(get("/api/resumen?periodo=2026-03&credito=true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.total").value(2000.00));
    }

    @Test
    @DisplayName("El ahorro es el mismo número en la pestaña débito y en la de crédito")
    void ahorroEsGlobalEnAmbasPestañas() throws Exception {
        crearIngreso("500000.00", "2026-03-01");
        crearGasto(deliveryId, "100000.00", "EFECTIVO", "2026-03-05");
        crearGasto(deliveryId, "50000.00", "CREDITO", "2026-02-10");

        JsonNode debito = resumen("2026-03", false);
        JsonNode credito = resumen("2026-03", true);

        assertThat(debito.get("balance").get("capacidadAhorro").decimalValue())
                .isEqualByComparingTo("350000.00");
        assertThat(debito.get("balance").get("capacidadAhorro").decimalValue())
                .isEqualByComparingTo(credito.get("balance").get("capacidadAhorro").decimalValue());
    }

    @Test
    @DisplayName("Sin ingresos cargados, tieneIngresos es false")
    void sinIngresosTieneIngresosEsFalse() throws Exception {
        crearGasto(deliveryId, "1000.00", "EFECTIVO", "2026-03-05");

        JsonNode resumen = resumen("2026-03", false);

        assertThat(resumen.get("balance").get("tieneIngresos").asBoolean()).isFalse();
        assertThat(resumen.get("balance").get("capacidadAhorro").decimalValue())
                .isEqualByComparingTo("-1000.00");
    }

    @Test
    @DisplayName("totalDelPeriodo sin gastos cargados devuelve cero, no null")
    void totalDelPeriodoSinGastosEsCero() {
        BigDecimal total = gastoRepository.totalDelPeriodo(
                usuarioId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), false);

        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
