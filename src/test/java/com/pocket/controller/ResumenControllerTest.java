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

    /** Crea la plantilla y la registra en el período: deja un `gasto` con origen FIJO. */
    private void crearGastoFijoRegistrado(Integer categoriaId, String monto, String medioPago,
                                          int diaDelMes, String periodo) throws Exception {
        String plantilla = """
                {
                  "descripcion": "fijo",
                  "monto": %s,
                  "categoriaId": %d,
                  "medioPago": "%s",
                  "diaDelMes": %d
                }
                """.formatted(monto, categoriaId, medioPago, diaDelMes);

        String creada = mockMvc.perform(post("/api/gastos-fijos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plantilla))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(creada).get("id").asText();

        String registro = """
                {
                  "periodo": "%s",
                  "monto": %s,
                  "idempotencyKey": "%s"
                }
                """.formatted(periodo, monto, UUID.randomUUID());

        mockMvc.perform(post("/api/gastos-fijos/" + id + "/registrar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registro))
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
            if (c.get("categoriaNombre").asText().equals(nombre)) return c;
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

        assertThat(resumen.get("avisoHormiga").get("categoriaNombre").asText())
                .isEqualTo("Delivery/Restaurantes");
        assertThat(resumen.get("avisoHormiga").get("ocurrencias").asLong()).isEqualTo(3);
        assertThat(resumen.get("avisoHormiga").get("porcentajeSobrePromedio").isNull()).isTrue();
    }

    @Test
    @DisplayName("Una compra en cuotas suma al total de la categoría pero no a sus ocurrencias (RN-02)")
    void cuotaNoCuentaComoHormiga() throws Exception {
        // La compra deja 1 cuota en marzo (comprada en febrero, imputada al mes siguiente).
        crearCompra(deliveryId, "6000.00", 6, "2026-02-15");
        // Dos gastos de crédito sueltos (no son cuotas) en la misma categoría y mes.
        crearGasto(deliveryId, "1000.00", "CREDITO", "2026-02-20");
        crearGasto(deliveryId, "1200.00", "CREDITO", "2026-02-25");

        JsonNode resumen = resumen("2026-03", true);

        JsonNode delivery = categoria(resumen, "Delivery/Restaurantes");

        // El total incluye la cuota: es plata que salió (RF-23). 1000 + 1200 + 1000
        // de la primera cuota de la compra de 6000 en 6.
        assertThat(delivery.get("total").asDouble()).isEqualTo(3200.00);

        // Las ocurrencias, en cambio, cuentan solo lo que puede ser hormiga: los
        // 2 gastos sueltos. Antes acá viajaba un 3 que incluía la cuota, y eso
        // hacía que la barra se pintara en rojo con 3 mientras el aviso decía que
        // no había hormiga: el mismo período contestado de dos formas distintas.
        assertThat(delivery.get("ocurrencias").asLong()).isEqualTo(2);
        assertThat(delivery.get("hormiga").asBoolean()).isFalse();

        // 2 ocurrencias reales, por debajo del umbral (3): no hay aviso.
        assertThat(resumen.get("avisoHormiga").isNull()).isTrue();
    }

    @Test
    @DisplayName("Un gasto fijo suma al total de la categoría pero tampoco cuenta como hormiga (RN-02)")
    void fijoNoCuentaComoHormiga() throws Exception {
        // Tres gastos de la misma categoría en el mes: con el umbral en 3 esto
        // sería hormiga… salvo que uno de los tres sea un fijo.
        crearGasto(deliveryId, "1000.00", "EFECTIVO", "2026-03-05");
        crearGasto(deliveryId, "1200.00", "EFECTIVO", "2026-03-12");
        crearGastoFijoRegistrado(deliveryId, "3000.00", "DEBITO", 20, "2026-03");

        JsonNode resumen = resumen("2026-03", false);
        JsonNode delivery = categoria(resumen, "Delivery/Restaurantes");

        assertThat(delivery.get("total").asDouble()).isEqualTo(5200.00);
        assertThat(delivery.get("ocurrencias").asLong()).isEqualTo(2);
        assertThat(delivery.get("hormiga").asBoolean()).isFalse();
        assertThat(resumen.get("avisoHormiga").isNull()).isTrue();
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
        crearIngreso("500000.00", "2026-03");
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
    @DisplayName("Sin ingresos cargados, el balance entero viaja en null (RF-33)")
    void sinIngresosElBalanceEsNull() throws Exception {
        crearGasto(deliveryId, "1000.00", "EFECTIVO", "2026-03-05");

        JsonNode resumen = resumen("2026-03", false);

        // Null y no un -1000 calculado: sin ingreso cargado no hay capacidad de
        // ahorro que mostrar, y un número igual serviría para pintarla mal.
        assertThat(resumen.get("balance").isNull()).isTrue();
    }

    @Test
    @DisplayName("totalDelPeriodo sin gastos cargados devuelve cero, no null")
    void totalDelPeriodoSinGastosEsCero() {
        BigDecimal total = gastoRepository.totalDelPeriodo(
                usuarioId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), false);

        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Con 1 mes de historia (solo el actual), el promedio no está disponible (RN-05)")
    void unMesDeHistoriaPromedioNoDisponible() throws Exception {
        crearGasto(deliveryId, "1000.00", "EFECTIVO", "2026-03-05");

        JsonNode resumen = resumen("2026-03", false);

        assertThat(resumen.get("promedioHistorico").isNull()).isTrue();
    }

    @Test
    @DisplayName("Con 4 meses de historia, el promedio es el de los 3 anteriores y los meses vacíos no lo distorsionan")
    void promedioDeLosTresMesesAnterioresSinContarElActual() throws Exception {
        crearGasto(deliveryId, "1000.00", "EFECTIVO", "2025-12-10"); // diciembre: 1000
        crearGasto(deliveryId, "2000.00", "EFECTIVO", "2026-01-15"); // enero: 2000
        // febrero queda sin gastos: un mes vacío dentro de la ventana.
        crearGasto(deliveryId, "9999.00", "EFECTIVO", "2026-03-05"); // marzo (actual): no entra en el promedio

        JsonNode resumen = resumen("2026-03", false);

        // (1000 + 2000 + 0) / 3 meses = 1000.00: febrero cuenta como $0, no se descarta del divisor.
        assertThat(resumen.get("promedioHistorico").decimalValue())
                .isEqualByComparingTo("1000.00");
        // El gasto actual (9999) supera holgadamente el promedio (1000). La
        // comparación es del cliente: el backend manda los dos números.
        assertThat(resumen.get("total").decimalValue())
                .isGreaterThan(resumen.get("promedioHistorico").decimalValue());
    }

    @Test
    @DisplayName("Un mes que gasta menos que el promedio queda por debajo de él")
    void gastoPorDebajoDelPromedioNoSuperaPromedio() throws Exception {
        crearGasto(deliveryId, "3000.00", "EFECTIVO", "2025-12-10");
        crearGasto(deliveryId, "3000.00", "EFECTIVO", "2026-01-15");
        crearGasto(deliveryId, "3000.00", "EFECTIVO", "2026-02-15");
        crearGasto(deliveryId, "100.00", "EFECTIVO", "2026-03-05");

        JsonNode resumen = resumen("2026-03", false);

        assertThat(resumen.get("promedioHistorico").decimalValue())
                .isEqualByComparingTo("3000.00");
        assertThat(resumen.get("total").decimalValue())
                .isLessThan(resumen.get("promedioHistorico").decimalValue());
    }
}
