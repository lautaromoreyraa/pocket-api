package com.pocket.dto.audio;

import com.pocket.enumeration.MedioPago;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GastoDetectado(
        UUID idempotencyKey,
        BigDecimal monto,
        Integer categoriaId,
        String descripcion,
        MedioPago medioPago,
        LocalDate fechaGasto,

        Integer cantidadCuotas
) {}
