package com.pocket.dto.ingreso;

import jakarta.validation.constraints.*;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.YearMonth;

public record IngresoRequest(

        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        @Digits(integer = 10, fraction = 2)
        BigDecimal monto,

        @Size(max = 255)
        String descripcion,

        @NotNull(message = "El período es obligatorio")
        @JsonFormat(pattern = "yyyy-MM")
        YearMonth periodo
) {}
