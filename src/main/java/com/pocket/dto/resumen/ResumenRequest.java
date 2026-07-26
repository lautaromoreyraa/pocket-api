package com.pocket.dto.resumen;

import jakarta.validation.constraints.NotNull;

import java.time.YearMonth;

public record ResumenRequest(

        @NotNull(message = "El período es obligatorio")
        YearMonth periodo,

        boolean credito
) {}
