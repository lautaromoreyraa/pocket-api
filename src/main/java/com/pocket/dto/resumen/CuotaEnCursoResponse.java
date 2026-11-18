package com.pocket.dto.resumen;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

public record CuotaEnCursoResponse(
        UUID id,
        String descripcion,
        String categoriaNombre,
        BigDecimal montoTotal,
        BigDecimal montoCuota,
        int cuotasPagas,
        int cantidadCuotas,
        YearMonth ultimoPeriodo
) {}
