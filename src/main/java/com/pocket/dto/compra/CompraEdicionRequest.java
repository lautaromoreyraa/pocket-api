package com.pocket.dto.compra;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CompraEdicionRequest(

        @NotNull(message = "La categoría es obligatoria")
        Integer categoriaId,

        @Size(max = 255)
        String descripcion
) {}
