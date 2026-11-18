package com.pocket.dto.gastofijo;

import com.pocket.enumeration.MedioPago;

import java.math.BigDecimal;
import java.util.UUID;

public record GastoFijoResponse(
        UUID id,
        String descripcion,
        BigDecimal monto,
        Integer categoriaId,
        String categoriaNombre,
        MedioPago medioPago,
        Integer diaDelMes,
        boolean activo
) {}
