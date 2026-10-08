package com.pocket.dto.gastofijo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * POST /api/gastos-fijos/{id}/registrar — tildar el fijo del mes.
 *
 * El monto viaja en el cuerpo y no se toma de la plantilla porque los fijos son
 * variables: la luz, el gas y las expensas cambian todos los meses. La plantilla
 * dice cuánto esperabas; esto dice cuánto pagaste.
 */
public record RegistroFijoRequest(

        @NotNull(message = "El período es obligatorio")
        @JsonFormat(pattern = "yyyy-MM")
        YearMonth periodo,

        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        @Digits(integer = 10, fraction = 2)
        BigDecimal monto,

        /** Generada por el cliente, igual que en cualquier alta (RF-41). */
        @NotNull(message = "La clave de idempotencia es obligatoria")
        UUID idempotencyKey
) {}
