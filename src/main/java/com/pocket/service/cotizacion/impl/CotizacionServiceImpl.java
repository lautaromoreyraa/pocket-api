package com.pocket.service.cotizacion.impl;

import com.pocket.config.PocketProperties;
import com.pocket.dto.cotizacion.CotizacionResponse;
import com.pocket.mapper.cotizacion.CotizacionMapper;
import com.pocket.repository.CotizacionRepository;
import com.pocket.service.cotizacion.CotizacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class CotizacionServiceImpl implements CotizacionService {

    private final RestClient cotizacionRestClient;
    private final CotizacionRepository cotizacionRepository;
    private final CotizacionMapper cotizacionMapper;
    private final PocketProperties props;

    @Override
    public CotizacionResponse obtenerBlue() {
        // TODO: leer la última guardada; si está vencida, intentar refrescar.
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    @Scheduled(fixedRateString = "${pocket.moneda.refresco-cotizacion-minutos:30}",
            timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void refrescar() {
        // TODO: GET a la API; si responde, guardar. Si falla, loguear y seguir.
        log.debug("Refresco de cotización todavía no implementado");
    }
}
