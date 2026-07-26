package com.pocket.dto.resumen;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

public record CuotaEnCursoResponse(
        UUID compraId,
        String descripcion,
        int cuotaActual,
        int cantidadCuotas,
        BigDecimal montoCuota,
        YearMonth ultimoPeriodo
) {}
