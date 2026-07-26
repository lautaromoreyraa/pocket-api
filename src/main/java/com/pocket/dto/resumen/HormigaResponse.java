package com.pocket.dto.resumen;

import java.math.BigDecimal;

public record HormigaResponse(
        String categoria,
        long ocurrencias,
        BigDecimal total,
        BigDecimal variacionVsPromedio
) {}
