package com.pocket.dto.resumen;

import java.math.BigDecimal;

public record BalanceResponse(
        BigDecimal ingresos,
        BigDecimal gastosTotales,
        BigDecimal capacidadAhorro
) {}
