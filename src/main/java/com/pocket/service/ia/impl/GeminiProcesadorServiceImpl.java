package com.pocket.service.ia.impl;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Usuario;
import com.pocket.dto.audio.AudioResponse;
import com.pocket.repository.CategoriaRepository;
import com.pocket.service.ia.ProcesadorIAService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class GeminiProcesadorServiceImpl implements ProcesadorIAService {

    private final RestClient iaRestClient;
    private final PocketProperties props;
    private final CategoriaRepository categoriaRepository;

    @Override
    public AudioResponse extraerGastos(MultipartFile audio, Usuario usuario) {
        // TODO: 1) codificar el audio en base64
        //       2) armar el prompt con la lista fija de categorías (RF-34)
        //       3) POST al modelo, pidiendo respuesta en JSON puro
        //       4) parsear y mapear cada gasto a GastoRequest
        //       5) si la lista viene vacía, lanzar AudioNoComprendidoException
        throw new UnsupportedOperationException("Pendiente de implementar");
    }
}
