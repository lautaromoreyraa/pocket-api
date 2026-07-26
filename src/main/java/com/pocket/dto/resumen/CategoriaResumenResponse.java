package com.pocket.dto.resumen;

import java.math.BigDecimal;

public record CategoriaResumenResponse(
        Integer categoriaId,
        String nombre,
        String icono,
        String color,
        BigDecimal total,
        long ocurrencias,
        boolean hormiga
) {}
