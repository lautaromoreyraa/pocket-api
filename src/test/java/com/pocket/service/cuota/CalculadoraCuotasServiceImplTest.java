package com.pocket.service.cuota;

import com.pocket.config.PocketProperties;
import com.pocket.domain.*;
import com.pocket.service.cuota.impl.CalculadoraCuotasServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El test más importante del proyecto: verifica la regla de redondeo de
 * cuotas (RN-04). Clase pura, sin contexto de Spring.
 */
class CalculadoraCuotasServiceImplTest {

    private final CalculadoraCuotasServiceImpl calculadora =
            new CalculadoraCuotasServiceImpl(new PocketProperties());

    private CompraFinanciada compra(String total, int cuotas, LocalDate fecha) {
        return CompraFinanciada.builder()
                .usuario(new Usuario())
                .categoria(new Categoria())
                .montoTotal(new BigDecimal(total))
                .cantidadCuotas(cuotas)
                .fechaCompra(fecha)
                .build();
    }

    @Test
    @DisplayName("La suma de las cuotas coincide exactamente con el total")
    void sumaExacta() {
        List<Gasto> cuotas = calculadora.generarCuotas(
                compra("12500.00", 9, LocalDate.of(2026, 1, 15)));

        BigDecimal suma = cuotas.stream()
                .map(Gasto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(suma).isEqualByComparingTo("12500.00");
    }

    @Test
    @DisplayName("La ultima cuota absorbe la diferencia del redondeo")
    void ultimaAbsorbeDiferencia() {
        List<Gasto> cuotas = calculadora.generarCuotas(
                compra("12500.00", 9, LocalDate.of(2026, 1, 15)));

        assertThat(cuotas).hasSize(9);
        assertThat(cuotas.get(0).getMonto()).isEqualByComparingTo("1388.88");
        assertThat(cuotas.get(8).getMonto()).isEqualByComparingTo("1388.96");
    }

    @Test
    @DisplayName("La cuota 1 cae en el mes siguiente al de la compra")
    void cuotaUnoEnMesSiguiente() {
        List<Gasto> cuotas = calculadora.generarCuotas(
                compra("60000.00", 6, LocalDate.of(2026, 1, 15)));

        assertThat(YearMonth.from(cuotas.get(0).getFechaImputacion()))
                .isEqualTo(YearMonth.of(2026, 2));
        assertThat(YearMonth.from(cuotas.get(5).getFechaImputacion()))
                .isEqualTo(YearMonth.of(2026, 7));
    }

    @Test
    @DisplayName("Todas las cuotas quedan marcadas como cuota")
    void todasSonCuotas() {
        List<Gasto> cuotas = calculadora.generarCuotas(
                compra("30000.00", 3, LocalDate.of(2026, 3, 1)));

        assertThat(cuotas).allMatch(Gasto::esCuota);
        assertThat(cuotas).extracting(Gasto::getNroCuota).containsExactly(1, 2, 3);
    }

    @ParameterizedTest
    @DisplayName("La suma cierra exacta para cualquier combinacion")
    @CsvSource({
            "100.00, 3", "12500.00, 9", "999.99, 7",
            "45000.50, 12", "1.00, 2", "7777.77, 11"
    })
    void sumaCierraSiempre(String total, int n) {
        List<Gasto> cuotas = calculadora.generarCuotas(
                compra(total, n, LocalDate.of(2026, 5, 10)));

        BigDecimal suma = cuotas.stream()
                .map(Gasto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(suma).isEqualByComparingTo(total);
    }
}
