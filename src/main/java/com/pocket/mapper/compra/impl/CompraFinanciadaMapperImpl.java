package com.pocket.mapper.compra.impl;

import com.pocket.domain.CompraFinanciada;
import com.pocket.domain.Gasto;
import com.pocket.dto.compra.CompraFinanciadaResponse;
import com.pocket.dto.resumen.CuotaEnCursoResponse;
import com.pocket.mapper.compra.CompraFinanciadaMapper;
import com.pocket.mapper.gasto.GastoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;

@Component
@RequiredArgsConstructor
public class CompraFinanciadaMapperImpl implements CompraFinanciadaMapper {

    private final GastoMapper gastoMapper;

    @Override
    public CompraFinanciadaResponse aResponse(CompraFinanciada c) {
        if (c == null) return null;

        return new CompraFinanciadaResponse(
                c.getId(),
                c.getMontoTotal(),
                c.getCantidadCuotas(),
                c.getCategoria().getNombre(),
                c.getDescripcion(),
                c.getFechaCompra(),
                gastoMapper.aResponse(c.getCuotas())
        );
    }

    @Override
    public CuotaEnCursoResponse aCuotaEnCurso(CompraFinanciada c, YearMonth periodoActual) {
        if (c == null) return null;

        Gasto cuotaDelPeriodo = c.getCuotas().stream()
                .filter(g -> YearMonth.from(g.getFechaImputacion()).equals(periodoActual))
                .findFirst()
                .orElse(null);

        YearMonth ultimo = c.getCuotas().stream()
                .map(g -> YearMonth.from(g.getFechaImputacion()))
                .max(Comparator.naturalOrder())
                .orElse(periodoActual);

        return new CuotaEnCursoResponse(
                c.getId(),
                c.getDescripcion(),
                c.getCategoria().getNombre(),
                c.getMontoTotal(),
                cuotaDelPeriodo != null ? cuotaDelPeriodo.getMonto() : BigDecimal.ZERO,
                cuotaDelPeriodo != null ? cuotaDelPeriodo.getNroCuota() : 0,
                c.getCantidadCuotas(),
                ultimo
        );
    }
}
