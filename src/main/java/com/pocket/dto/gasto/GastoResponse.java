package com.pocket.dto.gasto;

import com.pocket.enumeration.MedioPago;
import com.pocket.enumeration.OrigenGasto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una fila de `gasto`. Una cuota también es un gasto (sección 8.1 del escenario):
 * se distingue porque trae `compraFinanciadaId`.
 */
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
        /** Cuándo ocurrió realmente. */
        LocalDate fechaGasto,
        /** A qué mes se le carga (RN-03). Todos los reportes filtran por esto. */
        LocalDate fechaImputacion,

        /** Null si no es cuota. Reemplaza al viejo flag `esCuota`. */
        UUID compraFinanciadaId,
        /** Null si no es cuota, >= 1 si lo es. */
        Integer nroCuota,
        /** Cantidad total de cuotas, para poder mostrar "2 / 6". */
        Integer cantidadCuotas,

        /**
         * RF-27 — la categoría de este gasto superó el umbral del mes.
         *
         * Solo viaja poblado dentro del resumen, que es donde existe el período
         * contra el cual contar. En el alta o la edición de un gasto suelto no
         * hay período de referencia: llega en `false` y `null`, y eso significa
         * "no calculado", no "no se repite".
         */
        boolean hormiga,
        /** Cuántas veces se repitió la categoría en el período. Null si no se calculó. */
        Long ocurrenciasCategoria
) {}
