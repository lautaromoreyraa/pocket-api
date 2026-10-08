package com.pocket.service.ia;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.domain.Usuario;
import com.pocket.dto.audio.AudioResponse;
import com.pocket.dto.ia.GroqChatResponse;
import com.pocket.dto.ia.GroqTranscripcionResponse;
import com.pocket.enumeration.MedioPago;
import com.pocket.exception.AudioNoComprendidoException;
import com.pocket.repository.CategoriaRepository;
import com.pocket.service.ia.impl.GroqProcesadorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroqProcesadorServiceImplTest {

    private RestClient groqRestClient;
    private RestClient.RequestBodyUriSpec bodyUriSpec;
    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;
    private CategoriaRepository categoriaRepository;
    private GroqProcesadorServiceImpl service;
    private PocketProperties props;

    private final Usuario usuario = Usuario.builder().id(UUID.randomUUID()).deviceUuid("dev").build();

    private final MockMultipartFile audio =
            new MockMultipartFile("audio", "gasto.m4a", "audio/mp4", "no-importa".getBytes());

    private Integer deliveryId;
    private Integer combustibleId;
    private Integer otrosId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        groqRestClient = mock(RestClient.class);
        bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);
        categoriaRepository = mock(CategoriaRepository.class);

        when(groqRestClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any(String[].class))).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        deliveryId = 1;
        combustibleId = 2;
        otrosId = 3;
        when(categoriaRepository.findAllByOrderByOrdenAsc()).thenReturn(List.of(
                categoria(deliveryId, "Delivery/Restaurantes"),
                categoria(combustibleId, "Combustible"),
                categoria(otrosId, Categoria.OTROS)));

        props = new PocketProperties();
        props.getIa().setIntentos(1);
        props.getIa().getGroq().setApiKey("test-key");
        props.getIa().getGroq().setModeloVoz("whisper-large-v3");
        props.getIa().getGroq().setModeloTexto("llama-3.3-70b-versatile");

        service = new GroqProcesadorServiceImpl(
                groqRestClient, props, categoriaRepository, new ObjectMapper(),
                new ArmadorDeGastos(props), new ReintentosDeIA(props));
    }

    @Test
    @DisplayName("Transcribe y después extrae: dos llamadas y los gastos del texto")
    void transcribeYExtrae() {
        responde("Gasté cinco mil en facturas y veinte mil de nafta", """
                {"gastos": [
                  {"monto": 5000, "categoria": "Delivery/Restaurantes", "descripcion": "facturas",
                   "medioPago": "EFECTIVO", "cantidadCuotas": null},
                  {"monto": 20000, "categoria": "Combustible", "descripcion": "nafta",
                   "medioPago": "DEBITO", "cantidadCuotas": null}
                ]}""");

        AudioResponse respuesta = service.extraerGastos(audio, usuario);

        assertThat(respuesta.detectados()).hasSize(2);
        assertThat(respuesta.detectados().get(0).categoriaId()).isEqualTo(deliveryId);
        assertThat(respuesta.detectados().get(1).categoriaId()).isEqualTo(combustibleId);
        assertThat(respuesta.detectados().get(1).medioPago()).isEqualTo(MedioPago.DEBITO);

        // Los dos endpoints, en orden: primero el de voz y después el de texto.
        verify(bodyUriSpec).uri("/audio/transcriptions");
        verify(bodyUriSpec).uri("/chat/completions");
    }

    @Test
    @DisplayName("La transcripción que se devuelve es la del modelo de voz, no la del de texto")
    void devuelveLaTranscripcionDelModeloDeVoz() {
        // El modelo de texto intenta colar su propia version del audio; la que
        // vale es la de quien efectivamente escucho.
        responde("Gasté cinco mil en facturas", """
                {"transcripcion": "otra cosa que yo me invento",
                 "gastos": [{"monto": 5000, "categoria": "Delivery/Restaurantes",
                             "descripcion": "facturas", "medioPago": "EFECTIVO",
                             "cantidadCuotas": null}]}""");

        AudioResponse respuesta = service.extraerGastos(audio, usuario);

        assertThat(respuesta.transcripcion()).isEqualTo("Gasté cinco mil en facturas");
    }

    @Test
    @DisplayName("Un monto que no está en la transcripción descarta la respuesta entera")
    void montoFabricadoDescartaTodo() {
        responde("Gasté cinco mil en facturas", """
                {"gastos": [
                  {"monto": 5000, "categoria": "Delivery/Restaurantes", "descripcion": "facturas",
                   "medioPago": "EFECTIVO", "cantidadCuotas": null},
                  {"monto": 150000, "categoria": "Combustible", "descripcion": "nafta",
                   "medioPago": "EFECTIVO", "cantidadCuotas": null}
                ]}""");

        assertThatThrownBy(() -> service.extraerGastos(audio, usuario))
                .isInstanceOf(AudioNoComprendidoException.class);
    }

    @Test
    @DisplayName("Una transcripción vacía corta antes de gastar la segunda llamada")
    void transcripcionVaciaNoLlamaAlModeloDeTexto() {
        when(responseSpec.body(GroqTranscripcionResponse.class))
                .thenReturn(new GroqTranscripcionResponse("   "));

        assertThatThrownBy(() -> service.extraerGastos(audio, usuario))
                .isInstanceOf(AudioNoComprendidoException.class);

        verify(bodyUriSpec, never()).uri("/chat/completions");
        verify(responseSpec, never()).body(GroqChatResponse.class);
    }

    @Test
    @DisplayName("Sin gastos en el texto se responde 'no te entendí', no una lista vacía")
    void sinGastosLanzaAudioNoComprendido() {
        responde("Hola qué tal", """
                {"gastos": []}""");

        assertThatThrownBy(() -> service.extraerGastos(audio, usuario))
                .isInstanceOf(AudioNoComprendidoException.class);
    }

    @Test
    @DisplayName("Una categoría que no existe cae en Otros")
    void categoriaDesconocidaCaeEnOtros() {
        responde("Gasté cinco mil en criptomonedas", """
                {"gastos": [{"monto": 5000, "categoria": "Inversiones", "descripcion": "cripto",
                             "medioPago": "EFECTIVO", "cantidadCuotas": null}]}""");

        AudioResponse respuesta = service.extraerGastos(audio, usuario);

        assertThat(respuesta.detectados().get(0).categoriaId()).isEqualTo(otrosId);
    }

    @Test
    @DisplayName("Los montos en lunfardo matchean contra el número detectado")
    void lunfardoMatcheaConElNumero() {
        responde("Puse cinco lucas de nafta", """
                {"gastos": [{"monto": 5000, "categoria": "Combustible", "descripcion": "nafta",
                             "medioPago": "EFECTIVO", "cantidadCuotas": null}]}""");

        AudioResponse respuesta = service.extraerGastos(audio, usuario);

        assertThat(respuesta.detectados()).hasSize(1);
    }

    @Test
    @DisplayName("Una respuesta sin choices se reporta como no interpretable")
    void respuestaSinChoicesFalla() {
        when(responseSpec.body(GroqTranscripcionResponse.class))
                .thenReturn(new GroqTranscripcionResponse("Gasté cinco mil en facturas"));
        when(responseSpec.body(GroqChatResponse.class))
                .thenReturn(new GroqChatResponse(List.of()));

        assertThatThrownBy(() -> service.extraerGastos(audio, usuario))
                .isInstanceOf(AudioNoComprendidoException.class);
    }

    @Test
    @DisplayName("Un JSON roto del modelo no se propaga como error de parseo")
    void jsonInvalidoLanzaAudioNoComprendido() {
        responde("Gasté cinco mil", "esto no es json");

        assertThatThrownBy(() -> service.extraerGastos(audio, usuario))
                .isInstanceOf(AudioNoComprendidoException.class);
    }

    /** Encadena las dos respuestas: la del modelo de voz y la del de texto. */
    private void responde(String transcripcion, String jsonDeGastos) {
        when(responseSpec.body(GroqTranscripcionResponse.class))
                .thenReturn(new GroqTranscripcionResponse(transcripcion));
        when(responseSpec.body(GroqChatResponse.class))
                .thenReturn(new GroqChatResponse(List.of(
                        new GroqChatResponse.Choice(
                                new GroqChatResponse.Mensaje(jsonDeGastos)))));
    }

    private Categoria categoria(Integer id, String nombre) {
        Categoria c = new Categoria();
        c.setId(id);
        c.setNombre(nombre);
        return c;
    }
}
