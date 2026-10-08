package com.pocket.dto.cotizacion;

import java.math.BigDecimal;
import java.time.Instant;

/** RF-38 — cotización del dólar blue. */
public record CotizacionResponse(
        BigDecimal compra,
        BigDecimal venta,
        Instant fechaActualizacion,
        /** RF-40 — true si vino del cache porque la API externa falló. */
        boolean desdeCache
) {}
