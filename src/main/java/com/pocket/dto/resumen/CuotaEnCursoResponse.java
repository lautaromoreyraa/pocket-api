package com.pocket.dto.resumen;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/** Una compra en cuotas todavía en curso, para el bloque "Cuotas en curso". */
public record CuotaEnCursoResponse(
        UUID id,
        String descripcion,
        String categoriaNombre,
        BigDecimal montoTotal,
        BigDecimal montoCuota,
        /** Cuántas cuotas ya se imputaron, incluida la del período que se está viendo. */
        int cuotasPagas,
        int cantidadCuotas,
        /** Período de la última cuota, para poder decir hasta cuándo se paga. */
        YearMonth ultimoPeriodo
) {}
