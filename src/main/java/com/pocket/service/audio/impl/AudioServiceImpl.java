package com.pocket.service.audio.impl;

import com.pocket.domain.Usuario;
import com.pocket.dto.audio.AudioResponse;
import com.pocket.exception.ArchivoInvalidoException;
import com.pocket.service.audio.AudioService;
import com.pocket.service.auth.AuthService;
import com.pocket.service.ia.ProcesadorIAService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioServiceImpl implements AudioService {

    private static final String PREFIJO_CONTENT_TYPE_AUDIO = "audio/";

    /**
     * Content types que, sin ser "audio/*", igual llegan de clientes reales
     * mandando audio: curl y Expo suelen mandar .m4a como
     * application/octet-stream, y a veces como video/mp4 (el contenedor MP4
     * también se usa para audio-only).
     */
    private static final List<String> CONTENT_TYPES_AUDIO_PLAUSIBLES =
            List.of("application/octet-stream", "video/mp4");

    private static final List<String> EXTENSIONES_AUDIO_VALIDAS =
            List.of(".m4a", ".mp3", ".wav", ".ogg", ".aac", ".flac", ".webm");

    private final ProcesadorIAService procesadorIA;
    private final AuthService authService;

    @Override
    public AudioResponse procesar(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new ArchivoInvalidoException("El audio está vacío");
        }

        String contentType = audio.getContentType();
        log.debug("Audio recibido: nombre={}, contentType={}", audio.getOriginalFilename(), contentType);

        if (!esAudioPlausible(contentType, audio.getOriginalFilename())) {
            throw new ArchivoInvalidoException("El archivo debe ser de tipo audio");
        }

        // No persiste: el alta ocurre recién cuando el usuario confirma (RF-08).
        Usuario usuario = authService.actual();
        return procesadorIA.extraerGastos(audio, usuario);
    }

    /**
     * El content-type solo no alcanza: varios clientes (curl, Expo) mandan
     * audio real bajo "application/octet-stream" o "video/mp4". Si el
     * content-type no es concluyente, la extensión del nombre de archivo
     * desempata. Se rechaza solo si ninguna de las dos señales es plausible.
     */
    private boolean esAudioPlausible(String contentType, String nombreArchivo) {
        if (contentType != null) {
            String tipoNormalizado = contentType.toLowerCase(Locale.ROOT);
            if (tipoNormalizado.startsWith(PREFIJO_CONTENT_TYPE_AUDIO)
                    || CONTENT_TYPES_AUDIO_PLAUSIBLES.contains(tipoNormalizado)) {
                return true;
            }
        }
        return tieneExtensionDeAudio(nombreArchivo);
    }

    private boolean tieneExtensionDeAudio(String nombreArchivo) {
        if (nombreArchivo == null) {
            return false;
        }
        String nombreNormalizado = nombreArchivo.toLowerCase(Locale.ROOT);
        return EXTENSIONES_AUDIO_VALIDAS.stream().anyMatch(nombreNormalizado::endsWith);
    }
}
