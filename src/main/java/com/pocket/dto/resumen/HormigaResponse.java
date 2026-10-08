package com.pocket.dto.resumen;

import java.math.BigDecimal;

/**
 * RF-27 — el aviso de gasto hormiga que se muestra arriba de todo.
 *
 * El resumen devuelve uno solo, el de mayor total: es un aviso, no un listado.
 * El desglose completo por categoría ya viaja en `porCategoria`, donde cada
 * fila trae su propio flag `hormiga`.
 */
public record HormigaResponse(
        String categoriaNombre,
        long ocurrencias,
        BigDecimal total,
        /** Diferencia porcentual contra el promedio. Null si todavía no hay promedio. */
        BigDecimal porcentajeSobrePromedio
) {}
