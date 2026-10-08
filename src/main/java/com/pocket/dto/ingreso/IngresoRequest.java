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

        /**
         * El ingreso se carga a un mes, no a un día. Viaja como "2026-08",
         * igual que el `periodo` de todos los demás endpoints; antes era un
         * LocalDate que el servicio normalizaba al día 1 igual, así que el día
         * era ruido que el cliente tenía que inventar.
         */
        @NotNull(message = "El período es obligatorio")
        @JsonFormat(pattern = "yyyy-MM")
        YearMonth periodo
) {}
