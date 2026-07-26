package com.pocket.service.cuota.impl;

import com.pocket.config.PocketProperties;
import com.pocket.domain.CompraFinanciada;
import com.pocket.domain.Gasto;
import com.pocket.enumeration.MedioPago;
import com.pocket.enumeration.OrigenGasto;
import com.pocket.exception.CuotasInvalidasException;
import com.pocket.service.cuota.CalculadoraCuotasService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalculadoraCuotasServiceImpl implements CalculadoraCuotasService {

    private final PocketProperties props;

    @Override
    public List<Gasto> generarCuotas(CompraFinanciada compra) {
        int n = compra.getCantidadCuotas();
        validar(compra, n);

        BigDecimal total = compra.getMontoTotal();
        BigDecimal base = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        BigDecimal ultima = total.subtract(base.multiply(BigDecimal.valueOf(n - 1L)));

        YearMonth periodo = YearMonth.from(compra.getFechaCompra())
                .plusMonths(props.getCuotas().getDesfasePrimeraCuota());

        List<Gasto> cuotas = new ArrayList<>(n);

        for (int i = 1; i <= n; i++) {
            cuotas.add(Gasto.builder()
                    .usuario(compra.getUsuario())
                    .categoria(compra.getCategoria())
                    .compraFinanciada(compra)
                    .idempotencyKey(UUID.randomUUID())
                    .monto(i == n ? ultima : base)
                    .descripcion(compra.getDescripcion())
                    .medioPago(MedioPago.CREDITO)
                    .origen(OrigenGasto.MANUAL)
                    .fechaGasto(compra.getFechaCompra())
                    .fechaImputacion(periodo.atDay(1))
                    .nroCuota(i)
                    .build());

            periodo = periodo.plusMonths(1);
        }

        return cuotas;
    }

    private void validar(CompraFinanciada compra, int n) {
        if (n < 1 || n > props.getCuotas().getMaximo()) {
            throw new CuotasInvalidasException(
                    "La cantidad de cuotas debe estar entre 1 y " + props.getCuotas().getMaximo());
        }
        if (compra.getMontoTotal() == null
                || compra.getMontoTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CuotasInvalidasException("El monto total debe ser mayor a cero");
        }
    }
}
