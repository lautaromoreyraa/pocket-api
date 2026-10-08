package com.pocket.dto.resumen;

import java.math.BigDecimal;

/** Una fila del gráfico "Por categoría" (RF-28). */
public record CategoriaResumenResponse(
        Integer categoriaId,
        String categoriaNombre,
        String icono,
        String color,
        BigDecimal total,
        long ocurrencias,
        /** RF-27 — la categoría superó el umbral de ocurrencias del mes. */
        boolean hormiga
) {}
