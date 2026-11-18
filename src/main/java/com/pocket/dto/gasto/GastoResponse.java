package com.pocket.dto.gasto;

import com.pocket.enumeration.MedioPago;
import com.pocket.enumeration.OrigenGasto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GastoResponse(
        UUID id,
        BigDecimal monto,
        Integer categoriaId,
        String categoriaNombre,
        String categoriaIcono,
        String categoriaColor,
        String descripcion,
        MedioPago medioPago,
        OrigenGasto origen,
        LocalDate fechaGasto,
        LocalDate fechaImputacion,

        UUID compraFinanciadaId,
        Integer nroCuota,
        Integer cantidadCuotas,

        boolean hormiga,
        Long ocurrenciasCategoria
) {}
