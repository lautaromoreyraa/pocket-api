package com.pocket.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocket.domain.Categoria;
import com.pocket.repository.CategoriaRepository;
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

import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GastoFijoControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CategoriaRepository categoriaRepository;

    private String token;
    private String periodo;
    /** Los ids de categoría no se hardcodean: cada test siembra la suya y
     *  @Transactional la revierte al terminar. */
    private Integer servicios;

    @BeforeEach
    void setUp() throws Exception {
        servicios = categoriaRepository.save(Categoria.builder()
                .nombre("Servicios").icono("plug").color("#8F887A").orden(1).build()).getId();
        token = obtenerToken("device-" + UUID.randomUUID());
        periodo = YearMonth.now().toString();
    }

    // --- ABM -----------------------------------------------------------------

    @Test
    @DisplayName("POST crea la plantilla y la devuelve con la categoría resuelta")
    void altaDevuelveLaPlantilla() throws Exception {
        mockMvc.perform(post("/api/gastos-fijos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plantilla("Internet", "45000.00", "DEBITO", 10, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descripcion").value("Internet"))
                .andExpect(jsonPath("$.categoriaNombre").value("Servicios"))
                .andExpect(jsonPath("$.diaDelMes").value(10))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    @DisplayName("El alta ignora un `activo` en false: pausado es una acción posterior")
    void altaSiempreNaceActiva() throws Exception {
        // El frontend directamente no manda el campo; que el default sea activo
        // es lo que hace que no haga falta.
        mockMvc.perform(post("/api/gastos-fijos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plantilla("Internet", "45000.00", "DEBITO", 10, null)))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    @DisplayName("Un día del mes fuera de 1..28 es un 400 con el campo señalado")
    void diaFueraDeRango() throws Exception {
        mockMvc.perform(post("/api/gastos-fijos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plantilla("Malo", "45000.00", "DEBITO", 31, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.diaDelMes").exists());
    }

    @Test
    @DisplayName("Un monto en cero es un 400")
    void montoEnCero() throws Exception {
        mockMvc.perform(post("/api/gastos-fijos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plantilla("Malo", "0", "DEBITO", 10, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT edita la plantilla y puede pausarla")
    void edicionPausa() throws Exception {
        String id = crearFijo("Internet", "45000.00", "DEBITO", 10);

        mockMvc.perform(put("/api/gastos-fijos/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plantilla("Internet", "48000.00", "DEBITO", 12, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(48000.00))
                .andExpect(jsonPath("$.diaDelMes").value(12))
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    @DisplayName("DELETE devuelve 204 y la plantilla deja de listarse")
    void bajaDejaDeListar() throws Exception {
        String id = crearFijo("Internet", "45000.00", "DEBITO", 10);

        mockMvc.perform(delete("/api/gastos-fijos/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/gastos-fijos").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Borrar la plantilla no borra el gasto del mes que ya se pagó (RF-44)")
    void bajaConservaLosGastosGenerados() throws Exception {
        String id = crearFijo("Internet", "45000.00", "DEBITO", 10);
        registrar(id, periodo, "45000.00");

        mockMvc.perform(delete("/api/gastos-fijos/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/gastos?periodo=" + periodo + "&credito=false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].origen").value("FIJO"))
                .andExpect(jsonPath("$[0].monto").value(45000.00));
    }

    @Test
    @DisplayName("El fijo de otro usuario es un 404")
    void fijoAjenoEs404() throws Exception {
        String id = crearFijo("Internet", "45000.00", "DEBITO", 10);
        String otro = obtenerToken("device-" + UUID.randomUUID());

        mockMvc.perform(delete("/api/gastos-fijos/" + id)
                        .header("Authorization", "Bearer " + otro))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sin token, todos los endpoints de fijos son 401")
    void sinTokenEs401() throws Exception {
        mockMvc.perform(get("/api/gastos-fijos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/gastos-fijos/resumen?periodo=" + periodo))
                .andExpect(status().isUnauthorized());
    }

    // --- El resumen y el tilde -----------------------------------------------

    @Test
    @DisplayName("El resumen arranca con todo sin tildar y el estimado en el monto de las plantillas")
    void resumenSinTildar() throws Exception {
        crearFijo("Internet", "45000.00", "DEBITO", 10);
        crearFijo("Gimnasio", "17000.00", "DEBITO", 20);

        mockMvc.perform(get("/api/gastos-fijos/resumen?periodo=" + periodo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo").value(periodo))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].gastoId").doesNotExist())
                .andExpect(jsonPath("$.totalEstimado").value(62000.00))
                .andExpect(jsonPath("$.totalRegistrado").value(0));
    }

    @Test
    @DisplayName("Tildar deja el gastoId en el resumen y el estimado pasa a usar el monto real")
    void tildarActualizaElResumen() throws Exception {
        String id = crearFijo("Internet", "45000.00", "DEBITO", 10);
        registrar(id, periodo, "52300.00");

        JsonNode resumen = resumen(periodo);

        assertThat(resumen.get("items").get(0).get("gastoId").isNull()).isFalse();
        assertThat(resumen.get("items").get(0).get("montoRegistrado").asDouble()).isEqualTo(52300.00);
        // La plantilla no se toca: sigue diciendo lo que se espera pagar.
        assertThat(resumen.get("items").get(0).get("fijo").get("monto").asDouble()).isEqualTo(45000.00);
        assertThat(resumen.get("totalEstimado").asDouble()).isEqualTo(52300.00);
        assertThat(resumen.get("totalRegistrado").asDouble()).isEqualTo(52300.00);
    }

    @Test
    @DisplayName("Destildar es borrar el gasto: el fijo vuelve a aparecer pendiente")
    void destildarDevuelveElFijoAPendiente() throws Exception {
        String id = crearFijo("Internet", "45000.00", "DEBITO", 10);
        registrar(id, periodo, "45000.00");

        String gastoId = resumen(periodo).get("items").get(0).get("gastoId").asText();

        // No hay endpoint propio para destildar: el tilde ES el gasto.
        mockMvc.perform(delete("/api/gastos/" + gastoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(resumen(periodo).get("items").get(0).get("gastoId").isNull()).isTrue();
    }

    @Test
    @DisplayName("Tildar dos veces el mismo mes es 409")
    void dobleTildeEs409() throws Exception {
        String id = crearFijo("Internet", "45000.00", "DEBITO", 10);
        registrar(id, periodo, "45000.00");

        mockMvc.perform(post("/api/gastos-fijos/" + id + "/registrar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registro(periodo, "45000.00", UUID.randomUUID())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Tildar un mes que todavía no empezó es 400")
    void periodoFuturoEs400() throws Exception {
        String id = crearFijo("Internet", "45000.00", "DEBITO", 10);

        mockMvc.perform(post("/api/gastos-fijos/" + id + "/registrar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registro(YearMonth.now().plusMonths(1).toString(), "45000.00", UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Tildar un fijo pausado es 409")
    void pausadoEs409() throws Exception {
        String id = crearFijo("Internet", "45000.00", "DEBITO", 10);

        mockMvc.perform(put("/api/gastos-fijos/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plantilla("Internet", "45000.00", "DEBITO", 10, false)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/gastos-fijos/" + id + "/registrar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registro(periodo, "45000.00", UUID.randomUUID())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT del registro corrige el monto del mes sin tocar la plantilla")
    void corregirMontoDelMes() throws Exception {
        String id = crearFijo("Luz", "18700.00", "DEBITO", 12);
        registrar(id, periodo, "18700.00");

        mockMvc.perform(put("/api/gastos-fijos/" + id + "/registro?periodo=" + periodo)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\": 24900.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(24900.00));

        JsonNode resumen = resumen(periodo);
        assertThat(resumen.get("items").get(0).get("montoRegistrado").asDouble()).isEqualTo(24900.00);
        assertThat(resumen.get("items").get(0).get("fijo").get("monto").asDouble()).isEqualTo(18700.00);
    }

    @Test
    @DisplayName("Corregir el monto de un mes sin tildar es 404")
    void corregirSinTildarEs404() throws Exception {
        String id = crearFijo("Luz", "18700.00", "DEBITO", 12);

        mockMvc.perform(put("/api/gastos-fijos/" + id + "/registro?periodo=" + periodo)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\": 24900.00}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un fijo de crédito se tilda en el mes que se paga pero aparece en la pestaña de crédito del mes siguiente (RN-03, RN-10)")
    void fijoDeCreditoSeImputaAlMesSiguiente() throws Exception {
        String id = crearFijo("Netflix", "12400.00", "CREDITO", 15);
        registrar(id, periodo, "12400.00");

        // Se tilda en el mes en que se paga…
        assertThat(resumen(periodo).get("items").get(0).get("gastoId").isNull()).isFalse();

        // …pero la plata se imputa al mes siguiente, y en la pestaña de crédito.
        String siguiente = YearMonth.now().plusMonths(1).toString();
        mockMvc.perform(get("/api/gastos?periodo=" + siguiente + "&credito=true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].origen").value("FIJO"));

        mockMvc.perform(get("/api/gastos?periodo=" + periodo + "&credito=false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- Helpers -------------------------------------------------------------

    private String obtenerToken(String deviceUuid) throws Exception {
        String respuesta = mockMvc.perform(post("/api/auth/dispositivo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceUuid\":\"" + deviceUuid + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return respuesta.split("\"token\":\"")[1].split("\"")[0];
    }

    private String plantilla(String descripcion, String monto, String medioPago,
                             int diaDelMes, Boolean activo) {
        String estado = activo == null ? "" : ",\n  \"activo\": " + activo;
        return """
                {
                  "descripcion": "%s",
                  "monto": %s,
                  "categoriaId": %d,
                  "medioPago": "%s",
                  "diaDelMes": %d%s
                }
                """.formatted(descripcion, monto, servicios, medioPago, diaDelMes, estado);
    }

    private String registro(String periodo, String monto, UUID clave) {
        return """
                {
                  "periodo": "%s",
                  "monto": %s,
                  "idempotencyKey": "%s"
                }
                """.formatted(periodo, monto, clave);
    }

    private String crearFijo(String descripcion, String monto, String medioPago, int diaDelMes)
            throws Exception {
        String respuesta = mockMvc.perform(post("/api/gastos-fijos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plantilla(descripcion, monto, medioPago, diaDelMes, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("id").asText();
    }

    private void registrar(String id, String periodo, String monto) throws Exception {
        mockMvc.perform(post("/api/gastos-fijos/" + id + "/registrar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registro(periodo, monto, UUID.randomUUID())))
                .andExpect(status().isCreated());
    }

    private JsonNode resumen(String periodo) throws Exception {
        String respuesta = mockMvc.perform(get("/api/gastos-fijos/resumen?periodo=" + periodo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta);
    }
}
