package com.pocket.service.ia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.domain.Usuario;
import com.pocket.dto.audio.AudioResponse;
import com.pocket.dto.audio.GastoDetectado;
import com.pocket.dto.ia.GeminiResponse;
import com.pocket.enumeration.MedioPago;
import com.pocket.exception.AudioNoComprendidoException;
import com.pocket.repository.CategoriaRepository;
import com.pocket.service.ia.impl.GeminiProcesadorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiProcesadorServiceImplTest {

    private RestClient iaRestClient;
    private RestClient.RequestBodyUriSpec bodyUriSpec;
    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;
    private CategoriaRepository categoriaRepository;
    private GeminiProcesadorServiceImpl service;

    private final Usuario usuario = Usuario.builder().id(UUID.randomUUID()).deviceUuid("dev").build();

    private Integer deliveryId;
    private Integer combustibleId;
    private Integer otrosId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        iaRestClient = mock(RestClient.class);
        bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);
        categoriaRepository = mock(CategoriaRepository.class);

        when(iaRestClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        // any(Object.class), no any(): body() está sobrecargado (Object /
        // StreamingHttpOutputMessage.Body) y any() sin tipo matchea contra la
        // sobrecarga más específica, no contra la que realmente se invoca.
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        deliveryId = 1;
        combustibleId = 2;
        otrosId = 3;
        List<Categoria> categorias = List.of(
                categoria(deliveryId, "Delivery/Restaurantes"),
                categoria(combustibleId, "Combustible"),
                categoria(otrosId, Categoria.OTROS));
        when(categoriaRepository.findAllByOrderByOrdenAsc()).thenReturn(categorias);

        PocketProperties props = new PocketProperties();
        props.getIa().setModelo("gemini-2.0-flash");
        props.getIa().setApiKey("test-key");

        service = new GeminiProcesadorServiceImpl(iaRestClient, props, categoriaRepository, new ObjectMapper());
    }

    private Categoria categoria(Integer id, String nombre) {
        return Categoria.builder().id(id).nombre(nombre).icono("i").color("#000").orden(id).build();
    }

    private MockMultipartFile audioValido() {
        return new MockMultipartFile("audio", "audio.mp3", "audio/mpeg", "contenido-de-prueba".getBytes());
    }

    private void gemeniResponde(String textoJson) {
        GeminiResponse.Part part = new GeminiResponse.Part(textoJson);
        GeminiResponse.Content content = new GeminiResponse.Content(List.of(part));
        GeminiResponse.Candidate candidate = new GeminiResponse.Candidate(content);
        when(responseSpec.body(GeminiResponse.class)).thenReturn(new GeminiResponse(List.of(candidate)));
    }

    @Test
    @DisplayName("Un audio con dos gastos devuelve dos GastoDetectado, cada uno con idempotencyKey distinta")
    void dosGastosDevuelveDosDetectados() {
        gemeniResponde("""
                {
                  "transcripcion": "Gasté cinco lucas en delivery y veinte mil de nafta",
                  "gastos": [
                    {"monto": 5000, "categoria": "Delivery/Restaurantes", "descripcion": "delivery", "medioPago": "EFECTIVO", "cantidadCuotas": null},
                    {"monto": 20000, "categoria": "Combustible", "descripcion": "nafta", "medioPago": "EFECTIVO", "cantidadCuotas": null}
                  ]
                }
                """);

        AudioResponse resp = service.extraerGastos(audioValido(), usuario);

        assertThat(resp.detectados()).hasSize(2);
        assertThat(resp.detectados().get(0).idempotencyKey())
                .isNotEqualTo(resp.detectados().get(1).idempotencyKey());
        assertThat(resp.transcripcion()).contains("cinco lucas");
    }

    @Test
    @DisplayName("Una categoría que no existe en la lista cae en Otros")
    void categoriaInexistenteCaeEnOtros() {
        gemeniResponde("""
                {
                  "transcripcion": "Gasté 3000 en velas para el cumple",
                  "gastos": [
                    {"monto": 3000, "categoria": "Cotillón", "descripcion": "velas", "medioPago": "EFECTIVO", "cantidadCuotas": null}
                  ]
                }
                """);

        AudioResponse resp = service.extraerGastos(audioValido(), usuario);

        assertThat(resp.detectados().get(0).categoriaId()).isEqualTo(otrosId);
    }

    @Test
    @DisplayName("Un audio sin gastos detectables lanza AudioNoComprendidoException")
    void sinGastosLanzaExcepcion() {
        gemeniResponde("""
                {
                  "transcripcion": "No se entendió nada",
                  "gastos": []
                }
                """);

        assertThatThrownBy(() -> service.extraerGastos(audioValido(), usuario))
                .isInstanceOf(AudioNoComprendidoException.class);
    }

    @Test
    @DisplayName("Una respuesta que no es JSON válido también lanza AudioNoComprendidoException")
    void jsonInvalidoLanzaExcepcion() {
        gemeniResponde("esto no es json");

        assertThatThrownBy(() -> service.extraerGastos(audioValido(), usuario))
                .isInstanceOf(AudioNoComprendidoException.class);
    }

    @Test
    @DisplayName("El JSON envuelto en backticks de markdown se limpia antes de parsear")
    void limpiaBackticksDeMarkdown() {
        gemeniResponde("""
                ```json
                {
                  "transcripcion": "Gasté 1000 en el kiosco",
                  "gastos": [
                    {"monto": 1000, "categoria": "Otros", "descripcion": "kiosco", "medioPago": "EFECTIVO", "cantidadCuotas": null}
                  ]
                }
                ```
                """);

        AudioResponse resp = service.extraerGastos(audioValido(), usuario);

        assertThat(resp.detectados()).hasSize(1);
        assertThat(resp.detectados().get(0).monto()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Medio de pago no reconocido cae en EFECTIVO por default")
    void medioPagoInvalidoCaeEnEfectivo() {
        gemeniResponde("""
                {
                  "transcripcion": "Gasté 500",
                  "gastos": [
                    {"monto": 500, "categoria": "Otros", "descripcion": "algo", "medioPago": "BITCOIN", "cantidadCuotas": null}
                  ]
                }
                """);

        AudioResponse resp = service.extraerGastos(audioValido(), usuario);

        assertThat(resp.detectados().get(0).medioPago()).isEqualTo(MedioPago.EFECTIVO);
    }

    @Test
    @DisplayName("Medio de pago CREDITO detectado se mapea correctamente")
    void detectaMedioPagoCredito() {
        gemeniResponde("""
                {
                  "transcripcion": "Compré con crédito en seis cuotas",
                  "gastos": [
                    {"monto": 60000, "categoria": "Otros", "descripcion": "compra", "medioPago": "CREDITO", "cantidadCuotas": 6}
                  ]
                }
                """);

        AudioResponse resp = service.extraerGastos(audioValido(), usuario);

        assertThat(resp.detectados().get(0).medioPago()).isEqualTo(MedioPago.CREDITO);
    }

    // --- cantidadCuotas ------------------------------------------------

    @Test
    @DisplayName("cantidadCuotas viene null cuando el audio no menciona cuotas")
    void cantidadCuotasNullSinMencion() {
        gemeniResponde("""
                {
                  "transcripcion": "Gasté cinco lucas en el supermercado",
                  "gastos": [
                    {"monto": 5000, "categoria": "Otros", "descripcion": "super", "medioPago": "EFECTIVO", "cantidadCuotas": null}
                  ]
                }
                """);

        AudioResponse resp = service.extraerGastos(audioValido(), usuario);

        assertThat(resp.detectados().get(0).cantidadCuotas()).isNull();
    }

    @Test
    @DisplayName("cantidadCuotas viene con valor cuando el audio dice \"con crédito en seis cuotas\"")
    void cantidadCuotasConValorCuandoSeMenciona() {
        GastoDetectado detectado = extraerUnico("""
                {
                  "transcripcion": "Compré una tele con crédito en seis cuotas",
                  "gastos": [
                    {"monto": 120000, "categoria": "Otros", "descripcion": "tele", "medioPago": "CREDITO", "cantidadCuotas": 6}
                  ]
                }
                """);

        assertThat(detectado.cantidadCuotas()).isEqualTo(6);
        assertThat(detectado.medioPago()).isEqualTo(MedioPago.CREDITO);
    }

    private GastoDetectado extraerUnico(String textoJson) {
        gemeniResponde(textoJson);
        AudioResponse resp = service.extraerGastos(audioValido(), usuario);
        assertThat(resp.detectados()).hasSize(1);
        return resp.detectados().get(0);
    }

    @Test
    @DisplayName("El audio se codifica en base64 y se manda inline junto al prompt, en una sola llamada")
    void codificaElAudioEnBase64YLoMandaInline() {
        gemeniResponde("""
                {"transcripcion": "x", "gastos": [{"monto": 100, "categoria": "Otros", "descripcion": "x", "medioPago": "EFECTIVO", "cantidadCuotas": null}]}
                """);

        service.extraerGastos(audioValido(), usuario);

        org.mockito.ArgumentCaptor<com.pocket.dto.ia.GeminiRequest> captor =
                org.mockito.ArgumentCaptor.forClass(com.pocket.dto.ia.GeminiRequest.class);
        org.mockito.Mockito.verify(bodySpec).body(captor.capture());

        com.pocket.dto.ia.GeminiRequest enviado = captor.getValue();
        assertThat(enviado.contents()).hasSize(1);
        List<com.pocket.dto.ia.GeminiRequest.Part> partes = enviado.contents().get(0).parts();
        assertThat(partes).hasSize(2);
        assertThat(partes.get(0).text()).contains("Delivery/Restaurantes"); // el prompt incluye las categorías
        assertThat(partes.get(1).inlineData().data())
                .isEqualTo(java.util.Base64.getEncoder().encodeToString("contenido-de-prueba".getBytes()));
    }
}
