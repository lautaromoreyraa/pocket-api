package com.pocket.service.ia;

import com.pocket.domain.Usuario;
import com.pocket.dto.audio.AudioResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ProcesadorIAService {

    AudioResponse extraerGastos(MultipartFile audio, Usuario usuario);
}
