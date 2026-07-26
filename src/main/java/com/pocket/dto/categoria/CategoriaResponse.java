package com.pocket.dto.categoria;

public record CategoriaResponse(
        Integer id,
        String nombre,
        String icono,
        String color,
        Integer orden
) {}
