package com.pocket.dto.resumen;

import java.math.BigDecimal;

public record HormigaResponse(
        String categoriaNombre,
        long ocurrencias,
        BigDecimal total,
        BigDecimal porcentajeSobrePromedio
) {}
