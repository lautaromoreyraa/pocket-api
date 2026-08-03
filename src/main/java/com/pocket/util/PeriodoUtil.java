package com.pocket.util;

import com.pocket.enumeration.MedioPago;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class PeriodoUtil {

    private PeriodoUtil() {}

    /** Primer día del mes (RN-01: el mes calendario va del 1 al último día). */
    public static LocalDate primerDia(YearMonth periodo) {
        return periodo.atDay(1);
    }

    /** Último día del mes, contemplando meses de 28, 29, 30 y 31 días. */
    public static LocalDate ultimoDia(YearMonth periodo) {
        return periodo.atEndOfMonth();
    }

    /** Mes en curso según la zona horaria configurada (RN-07). */
    public static YearMonth mesActual(String zonaHoraria) {
        return YearMonth.now(ZoneId.of(zonaHoraria));
    }

    /**
     * Período al que se imputa un gasto (RN-03).
     * Todo gasto con crédito se imputa al mes siguiente, tenga cuotas o no.
     * El resto se imputa al mes en que ocurrió.
     */
    public static YearMonth periodoDeImputacion(LocalDate fechaGasto,
                                                MedioPago medioPago,
                                                int desfaseCredito) {
        YearMonth base = YearMonth.from(fechaGasto);
        return medioPago.seImputaAlMesSiguiente() ? base.plusMonths(desfaseCredito) : base;
    }

    /** Los N meses anteriores al período dado, del más viejo al más nuevo. */
    public static List<YearMonth> mesesPrevios(YearMonth desde, int cantidad) {
        List<YearMonth> meses = new ArrayList<>(cantidad);
        for (int i = cantidad; i >= 1; i--) {
            meses.add(desde.minusMonths(i));
        }
        return meses;
    }

    /** Cantidad de meses completos entre dos períodos, para saber si hay historia (RN-05). */
    public static long mesesEntre(YearMonth desde, YearMonth hasta) {
        return java.time.temporal.ChronoUnit.MONTHS.between(desde, hasta);
    }

    /** Normaliza una fecha al primer día de su mes: así se guarda el período de un ingreso. */
    public static LocalDate normalizarPeriodo(LocalDate fecha) {
        return fecha.withDayOfMonth(1);
    }

    /**
     * Meses previos al período, acotados a la vez por la ventana configurada
     * (RN-05) y por cuándo arrancó la historia real de datos. Vacía si nunca
     * hubo gastos.
     *
     * Nota deliberada: si hubo un hueco de uso (ej. gastos en enero, nada en
     * todo el año, volvió en diciembre), esta ventana igual divide por los 12
     * meses completos aunque 11 estén vacíos — el promedio queda bajo. Es
     * aceptable para el MVP: acotar por huecos internos (no solo por el
     * primer período) queda para una iteración futura si hace falta.
     */
    public static List<YearMonth> ventanaPromedio(YearMonth periodo, YearMonth primerPeriodoConGastos, int ventanaMeses) {
        if (primerPeriodoConGastos == null) return List.of();
        long disponibles = mesesEntre(primerPeriodoConGastos, periodo);
        int cantidad = (int) Math.min(ventanaMeses, disponibles);
        return cantidad <= 0 ? List.of() : mesesPrevios(periodo, cantidad);
    }
}
