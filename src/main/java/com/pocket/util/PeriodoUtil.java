package com.pocket.util;

import com.pocket.enumeration.MedioPago;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class PeriodoUtil {

    private PeriodoUtil() {}

    public static LocalDate primerDia(YearMonth periodo) {
        return periodo.atDay(1);
    }

    public static LocalDate ultimoDia(YearMonth periodo) {
        return periodo.atEndOfMonth();
    }

    public static YearMonth mesActual(String zonaHoraria) {
        return YearMonth.now(ZoneId.of(zonaHoraria));
    }

    public static YearMonth periodoDeImputacion(LocalDate fechaGasto,
                                                MedioPago medioPago,
                                                int desfaseCredito) {
        YearMonth base = YearMonth.from(fechaGasto);
        return medioPago.seImputaAlMesSiguiente() ? base.plusMonths(desfaseCredito) : base;
    }

    public static List<YearMonth> mesesPrevios(YearMonth desde, int cantidad) {
        List<YearMonth> meses = new ArrayList<>(cantidad);
        for (int i = cantidad; i >= 1; i--) {
            meses.add(desde.minusMonths(i));
        }
        return meses;
    }

    public static long mesesEntre(YearMonth desde, YearMonth hasta) {
        return java.time.temporal.ChronoUnit.MONTHS.between(desde, hasta);
    }

    public static LocalDate normalizarPeriodo(LocalDate fecha) {
        return fecha.withDayOfMonth(1);
    }

    public static List<YearMonth> ventanaPromedio(YearMonth periodo, YearMonth primerPeriodoConGastos, int ventanaMeses) {
        if (primerPeriodoConGastos == null) return List.of();
        long disponibles = mesesEntre(primerPeriodoConGastos, periodo);
        int cantidad = (int) Math.min(ventanaMeses, disponibles);
        return cantidad <= 0 ? List.of() : mesesPrevios(periodo, cantidad);
    }
}
