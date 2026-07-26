package com.pocket.dto.cotizacion;

import java.math.BigDecimal;
import java.time.Instant;

public record CotizacionResponse(
        BigDecimal compra,
        BigDecimal venta,
        Instant actualizado,
        boolean desdeCache
) {}
