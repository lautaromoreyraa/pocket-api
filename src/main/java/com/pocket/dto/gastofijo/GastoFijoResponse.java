package com.pocket.dto.gastofijo;

import com.pocket.enumeration.MedioPago;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una plantilla de gasto fijo. Coincide campo por campo con `GastoFijo` del
 * frontend.
 *
 * `monto` es lo que se <b>espera</b> pagar cada mes. Lo que se pagó de verdad
 * vive en el `gasto` que se genera y viaja aparte, en `montoRegistrado`: la luz
 * viene distinta cada vez y la plantilla no se corrige sola por eso.
 */
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
