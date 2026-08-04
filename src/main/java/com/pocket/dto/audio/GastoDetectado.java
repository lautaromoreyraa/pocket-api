package com.pocket.dto.audio;

import com.pocket.enumeration.MedioPago;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lo que la IA detectó en el audio (RF-05). Deliberadamente distinto de
 * GastoRequest: agrega cantidadCuotas, que no existe en el gasto persistido
 * (vive en CompraRequest), y evita atar dos contratos que evolucionan por
 * separado.
 *
 * El backend solo reporta lo que escuchó; la app decide qué hacer con eso:
 * si cantidadCuotas viene con valor, arma un POST /api/compras; si viene
 * null, un POST /api/gastos.
 */
public record GastoDetectado(
        UUID idempotencyKey,
        BigDecimal monto,
        Integer categoriaId,
        String descripcion,
        MedioPago medioPago,
        LocalDate fechaGasto,

        /** Null si el audio no mencionó cuotas: ausencia de dato, no "una cuota". */
        Integer cantidadCuotas
) {}
