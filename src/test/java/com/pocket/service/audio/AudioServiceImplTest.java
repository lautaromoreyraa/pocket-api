package com.pocket.service.audio;

import com.pocket.domain.Usuario;
import com.pocket.dto.audio.AudioResponse;
import com.pocket.dto.audio.GastoDetectado;
import com.pocket.enumeration.MedioPago;
import com.pocket.exception.ArchivoInvalidoException;
import com.pocket.service.audio.impl.AudioServiceImpl;
import com.pocket.service.auth.AuthService;
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
    private AudioServiceImpl service;

    private final Usuario usuario = Usuario.builder().id(UUID.randomUUID()).deviceUuid("dev").build();

    @BeforeEach
    void setUp() {
        procesadorIA = mock(ProcesadorIAService.class);
        authService = mock(AuthService.class);
        service = new AudioServiceImpl(procesadorIA, authService);
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
    @DisplayName("Un archivo que no es de tipo audio lanza ArchivoInvalidoException")
    void tipoNoAudioLanzaExcepcion() {
        MockMultipartFile pdf = new MockMultipartFile("audio", "doc.pdf", "application/pdf", "x".getBytes());

        assertThatThrownBy(() -> service.procesar(pdf))
                .isInstanceOf(ArchivoInvalidoException.class);
        verify(procesadorIA, never()).extraerGastos(any(), any());
    }

    @Test
    @DisplayName("Un content type nulo lanza ArchivoInvalidoException")
    void contentTypeNuloLanzaExcepcion() {
        MockMultipartFile sinTipo = new MockMultipartFile("audio", "audio", null, "x".getBytes());

        assertThatThrownBy(() -> service.procesar(sinTipo))
                .isInstanceOf(ArchivoInvalidoException.class);
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
