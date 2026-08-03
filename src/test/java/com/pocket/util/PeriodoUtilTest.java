package com.pocket.util;

import com.pocket.enumeration.MedioPago;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

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

    @Test
    @DisplayName("ventanaPromedio: sin historia previa, la ventana queda vacia")
    void ventanaPromedioSinHistoria() {
        List<YearMonth> ventana = PeriodoUtil.ventanaPromedio(YearMonth.of(2026, 3), null, 12);

        assertThat(ventana).isEmpty();
    }

    @Test
    @DisplayName("ventanaPromedio: se acota a los meses reales de historia, no a la ventana configurada")
    void ventanaPromedioAcotadaPorLaHistoria() {
        // Primer gasto en diciembre 2025: solo 3 meses previos a marzo 2026,
        // aunque la ventana configurada sea de 12.
        List<YearMonth> ventana = PeriodoUtil.ventanaPromedio(
                YearMonth.of(2026, 3), YearMonth.of(2025, 12), 12);

        assertThat(ventana).containsExactly(
                YearMonth.of(2025, 12), YearMonth.of(2026, 1), YearMonth.of(2026, 2));
    }

    @Test
    @DisplayName("ventanaPromedio: se acota a la ventana configurada aunque haya mas historia")
    void ventanaPromedioAcotadaPorLaVentana() {
        // Un año de historia, pero la ventana configurada es de 3 meses.
        List<YearMonth> ventana = PeriodoUtil.ventanaPromedio(
                YearMonth.of(2026, 3), YearMonth.of(2020, 1), 3);

        assertThat(ventana).containsExactly(
                YearMonth.of(2025, 12), YearMonth.of(2026, 1), YearMonth.of(2026, 2));
    }
}
