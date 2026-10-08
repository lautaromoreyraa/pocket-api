package com.pocket.service.cotizacion.impl;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Cotizacion;
import com.pocket.dto.cotizacion.CotizacionApiResponse;
import com.pocket.dto.cotizacion.CotizacionResponse;
import com.pocket.exception.CotizacionNoDisponibleException;
import com.pocket.mapper.cotizacion.CotizacionMapper;
import com.pocket.repository.CotizacionRepository;
import com.pocket.service.cotizacion.CotizacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
        Optional<Cotizacion> guardada = cotizacionRepository.findFirstByOrderByFechaActualizacionDesc();

        if (guardada.isEmpty() || estaVencida(guardada.get())) {
            boolean refrescoOk = intentarRefrescar();
            if (refrescoOk) {
                guardada = cotizacionRepository.findFirstByOrderByFechaActualizacionDesc();
            }
            if (guardada.isEmpty()) {
                throw new CotizacionNoDisponibleException(
                        "No hay cotización del dólar blue disponible: la API no respondió "
                                + "y no hay ninguna guardada");
            }
            // desdeCache sale directo del resultado del refresh, no de comparar filas:
            // con la fila única (sin historial), esa comparación sería frágil ante
            // dos requests concurrentes disparando el refresh al mismo tiempo.
            return cotizacionMapper.aResponse(guardada.get(), !refrescoOk);
        }

        return cotizacionMapper.aResponse(guardada.get(), false);
    }

    @Override
    @Scheduled(fixedRateString = "${pocket.moneda.refresco-cotizacion-minutos:30}",
            timeUnit = TimeUnit.MINUTES)
    public void refrescar() {
        intentarRefrescar();
    }

    /**
     * Pide la cotización a la API y la guarda. Nunca propaga: es el método
     * que corre tanto desde el @Scheduled (una excepción ahí ensuciaría los
     * logs cada 30 minutos sin que nadie se entere) como desde obtenerBlue()
     * cuando la guardada está vencida.
     *
     * Guarda siempre en la MISMA fila (actualiza si ya hay una, inserta si
     * no): solo interesa el último valor, no un histórico de cotizaciones.
     *
     * Sin @Transactional acá a propósito: en un método privado la anotación
     * no tendría efecto (Spring no proxea llamadas privadas/self-invocadas).
     * El find y el save ya son atómicos cada uno por su cuenta vía
     * SimpleJpaRepository, que alcanza para este caso.
     */
    private boolean intentarRefrescar() {
        try {
            CotizacionApiResponse body = cotizacionRestClient.get()
                    .uri(props.getCotizacion().getUrl())
                    .retrieve()
                    .body(CotizacionApiResponse.class);

            if (body == null || body.compra() == null || body.venta() == null) {
                log.warn("Cotización blue: la API devolvió una respuesta vacía o incompleta");
                return false;
            }

            Cotizacion cotizacion = cotizacionRepository.findFirstByOrderByFechaActualizacionDesc()
                    .orElseGet(Cotizacion::new);
            cotizacion.setValorCompra(body.compra());
            cotizacion.setValorVenta(body.venta());
            cotizacion.setFechaActualizacion(Instant.now());
            cotizacionRepository.save(cotizacion);
            return true;
        } catch (Exception e) {
            log.warn("No se pudo refrescar la cotización del dólar blue: {}", e.getMessage());
            return false;
        }
    }

    private boolean estaVencida(Cotizacion cotizacion) {
        Duration antiguedad = Duration.between(cotizacion.getFechaActualizacion(), Instant.now());
        Duration maximo = Duration.ofMinutes(props.getMoneda().getRefrescoCotizacionMinutos());
        return antiguedad.compareTo(maximo) >= 0;
    }
}
