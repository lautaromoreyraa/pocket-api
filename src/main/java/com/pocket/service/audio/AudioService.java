package com.pocket.service.audio;

import com.pocket.dto.audio.AudioResponse;
import org.springframework.web.multipart.MultipartFile;

public interface AudioService {

    AudioResponse procesar(MultipartFile audio);
}
