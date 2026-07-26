package com.pocket.service.ia;

import com.pocket.domain.Usuario;
import com.pocket.dto.audio.AudioResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ProcesadorIAService {

    /**
     * Extrae los gastos de un audio (RF-05). No persiste nada.
     *
     * @throws com.pocket.exception.AudioNoComprendidoException si no detecta gastos (RF-09)
     */
    AudioResponse extraerGastos(MultipartFile audio, Usuario usuario);
}
