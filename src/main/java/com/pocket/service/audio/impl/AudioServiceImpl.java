package com.pocket.service.audio.impl;

import com.pocket.domain.Usuario;
import com.pocket.dto.audio.AudioResponse;
import com.pocket.exception.ArchivoInvalidoException;
import com.pocket.service.audio.AudioService;
import com.pocket.service.auth.AuthService;
import com.pocket.service.ia.ProcesadorIAService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AudioServiceImpl implements AudioService {

    private static final String PREFIJO_CONTENT_TYPE_AUDIO = "audio/";

    private final ProcesadorIAService procesadorIA;
    private final AuthService authService;

    @Override
    public AudioResponse procesar(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new ArchivoInvalidoException("El audio está vacío");
        }

        String contentType = audio.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith(PREFIJO_CONTENT_TYPE_AUDIO)) {
            throw new ArchivoInvalidoException("El archivo debe ser de tipo audio");
        }

        // No persiste: el alta ocurre recién cuando el usuario confirma (RF-08).
        Usuario usuario = authService.actual();
        return procesadorIA.extraerGastos(audio, usuario);
    }
}
