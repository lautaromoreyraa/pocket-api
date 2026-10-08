package com.pocket.service.audio;

import com.pocket.domain.Usuario;
import com.pocket.dto.audio.AudioResponse;
import com.pocket.dto.audio.GastoDetectado;
import com.pocket.enumeration.MedioPago;
import com.pocket.exception.ArchivoInvalidoException;
import com.pocket.service.audio.impl.AudioServiceImpl;
import com.pocket.service.auth.AuthService;
import com.pocket.service.audio.LimitadorDeAudio;
import com.pocket.service.ia.ProcesadorIAService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioServiceImplTest {

    private ProcesadorIAService procesadorIA;
    private AuthService authService;
    private LimitadorDeAudio limitador;
    private AudioServiceImpl service;

    private final Usuario usuario = Usuario.builder().id(UUID.randomUUID()).deviceUuid("dev").build();

    @BeforeEach
    void setUp() {
        procesadorIA = mock(ProcesadorIAService.class);
        authService = mock(AuthService.class);
        limitador = mock(LimitadorDeAudio.class);
        service = new AudioServiceImpl(procesadorIA, authService, limitador);
    }

    @Test
    @DisplayName("Un archivo vacío lanza ArchivoInvalidoException")
    void archivoVacioLanzaExcepcion() {
        MockMultipartFile vacio = new MockMultipartFile("audio", "audio.mp3", "audio/mpeg", new byte[0]);

        assertThatThrownBy(() -> service.procesar(vacio))
                .isInstanceOf(ArchivoInvalidoException.class);
        verify(procesadorIA, never()).extraerGastos(any(), any());
    }

    @Test
    @DisplayName("Un archivo claramente no-audio (texto, extensión .txt) lanza ArchivoInvalidoException")
    void tipoNoAudioLanzaExcepcion() {
        MockMultipartFile texto = new MockMultipartFile("audio", "notas.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> service.procesar(texto))
                .isInstanceOf(ArchivoInvalidoException.class);
        verify(procesadorIA, never()).extraerGastos(any(), any());
    }

    @Test
    @DisplayName("Content type nulo y sin extensión reconocible lanza ArchivoInvalidoException")
    void sinContentTypeNiExtensionLanzaExcepcion() {
        MockMultipartFile sinTipo = new MockMultipartFile("audio", "archivo", null, "x".getBytes());

        assertThatThrownBy(() -> service.procesar(sinTipo))
                .isInstanceOf(ArchivoInvalidoException.class);
    }

    @Test
    @DisplayName("application/octet-stream con extensión .m4a se acepta (así lo mandan curl y Expo)")
    void octetStreamConExtensionM4aSeAcepta() {
        MockMultipartFile audio =
                new MockMultipartFile("audio", "grabacion.m4a", "application/octet-stream", "x".getBytes());
        when(authService.actual()).thenReturn(usuario);
        GastoDetectado detectado = new GastoDetectado(
                UUID.randomUUID(), new BigDecimal("1000"), 1, "x", MedioPago.EFECTIVO, LocalDate.now(), null);
        AudioResponse respuestaEsperada = new AudioResponse(List.of(detectado), "transcripción");
        when(procesadorIA.extraerGastos(audio, usuario)).thenReturn(respuestaEsperada);

        AudioResponse resp = service.procesar(audio);

        assertThat(resp).isSameAs(respuestaEsperada);
    }

    @Test
    @DisplayName("video/mp4 (contenedor que a veces trae audio-only) se acepta")
    void videoMp4SeAcepta() {
        MockMultipartFile audio = new MockMultipartFile("audio", "audio.mp4", "video/mp4", "x".getBytes());
        when(authService.actual()).thenReturn(usuario);
        GastoDetectado detectado = new GastoDetectado(
                UUID.randomUUID(), new BigDecimal("1000"), 1, "x", MedioPago.EFECTIVO, LocalDate.now(), null);
        when(procesadorIA.extraerGastos(audio, usuario)).thenReturn(new AudioResponse(List.of(detectado), "x"));

        assertThat(service.procesar(audio)).isNotNull();
    }

    @Test
    @DisplayName("Un audio válido delega en el procesador de IA y no persiste nada")
    void audioValidoDelegaEnElProcesador() {
        MockMultipartFile audio = new MockMultipartFile("audio", "audio.mp3", "audio/mpeg", "x".getBytes());
        when(authService.actual()).thenReturn(usuario);
        GastoDetectado detectado = new GastoDetectado(
                UUID.randomUUID(), new BigDecimal("1000"), 1, "x", MedioPago.EFECTIVO, LocalDate.now(), null);
        AudioResponse respuestaEsperada = new AudioResponse(List.of(detectado), "transcripción");
        when(procesadorIA.extraerGastos(audio, usuario)).thenReturn(respuestaEsperada);

        AudioResponse resp = service.procesar(audio);

        assertThat(resp).isSameAs(respuestaEsperada);
        verify(procesadorIA).extraerGastos(eq(audio), eq(usuario));
    }
}
