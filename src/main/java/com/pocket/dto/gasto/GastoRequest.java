package com.pocket.dto.gasto;

import com.pocket.enumeration.MedioPago;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GastoRequest(

        /** Generada por el cliente. Evita duplicados al reintentar (RF-41). */
        @NotNull(message = "La clave de idempotencia es obligatoria")
        UUID idempotencyKey,

        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        @Digits(integer = 10, fraction = 2)
        BigDecimal monto,

        @NotNull(message = "La categoría es obligatoria")
        Integer categoriaId,

        @Size(max = 255)
        String descripcion,

        @NotNull(message = "El medio de pago es obligatorio")
        MedioPago medioPago,

        @NotNull(message = "La fecha del gasto es obligatoria")
        @PastOrPresent(message = "La fecha no puede ser futura")
        LocalDate fechaGasto
) {}
