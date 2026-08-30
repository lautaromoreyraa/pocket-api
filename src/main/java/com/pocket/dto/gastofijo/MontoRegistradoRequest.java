package com.pocket.dto.gastofijo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** PUT /api/gastos-fijos/{id}/registro?periodo= — corregir cuánto se pagó este mes. */
public record MontoRegistradoRequest(

        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        @Digits(integer = 10, fraction = 2)
        BigDecimal monto
) {}
