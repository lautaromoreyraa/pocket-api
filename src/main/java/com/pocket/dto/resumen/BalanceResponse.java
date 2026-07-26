package com.pocket.dto.resumen;

import java.math.BigDecimal;

public record BalanceResponse(
        BigDecimal ingresos,
        BigDecimal gastosDebito,
        BigDecimal gastosCredito,
        BigDecimal capacidadAhorro,
        boolean tieneIngresos,
        BigDecimal promedioHistorico,
        boolean promedioDisponible,
        boolean superaPromedio
) {}
