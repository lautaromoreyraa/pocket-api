package com.pocket.util;

import com.pocket.enumeration.MedioPago;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodoUtilTest {

    @Test
    @DisplayName("Los gastos de credito se imputan al mes siguiente")
    void creditoAlMesSiguiente() {
        YearMonth imputacion = PeriodoUtil.periodoDeImputacion(
                LocalDate.of(2026, 1, 20), MedioPago.CREDITO, 1);

        assertThat(imputacion).isEqualTo(YearMonth.of(2026, 2));
    }

    @Test
    @DisplayName("Los gastos de debito se imputan al mes en curso")
    void debitoAlMesActual() {
        YearMonth imputacion = PeriodoUtil.periodoDeImputacion(
                LocalDate.of(2026, 1, 20), MedioPago.DEBITO, 1);

        assertThat(imputacion).isEqualTo(YearMonth.of(2026, 1));
    }

    @Test
    @DisplayName("El credito de diciembre se imputa a enero del anio siguiente")
    void cruceDeAnio() {
        YearMonth imputacion = PeriodoUtil.periodoDeImputacion(
                LocalDate.of(2026, 12, 28), MedioPago.CREDITO, 1);

        assertThat(imputacion).isEqualTo(YearMonth.of(2027, 1));
    }

    @Test
    @DisplayName("El ultimo dia contempla febrero bisiesto")
    void febreroBisiesto() {
        assertThat(PeriodoUtil.ultimoDia(YearMonth.of(2028, 2)))
                .isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(PeriodoUtil.ultimoDia(YearMonth.of(2026, 2)))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }
}
