package com.pocket.dto.compra;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Edición de una compra financiada: solo categoría y descripción, propagadas a
 * todas las cuotas. Los montos, la cantidad de cuotas y las fechas son
 * inmutables, por eso no se exponen acá.
 */
public record CompraEdicionRequest(

        @NotNull(message = "La categoría es obligatoria")
        Integer categoriaId,

        @Size(max = 255)
        String descripcion
) {}
