package com.pocket.service.audio.impl;

import com.pocket.dto.audio.AudioResponse;
import com.pocket.service.audio.AudioService;
import com.pocket.service.auth.AuthService;
import com.pocket.service.ia.ProcesadorIAService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AudioServiceImpl implements AudioService {

    private final ProcesadorIAService procesadorIA;
    private final AuthService authService;

    @Override
    public AudioResponse procesar(MultipartFile audio) {
        // TODO: validar tamaño y tipo, delegar en el procesador de IA.
        //       No persiste: el usuario confirma antes de guardar (RF-08).
        throw new UnsupportedOperationException("Pendiente de implementar");
    }
}
