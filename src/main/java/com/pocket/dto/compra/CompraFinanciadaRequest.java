package com.pocket.dto.compra;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CompraFinanciadaRequest(

        @NotNull
        UUID idempotencyKey,

        @NotNull(message = "El monto total es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        @Digits(integer = 10, fraction = 2)
        BigDecimal montoTotal,

        @NotNull(message = "La cantidad de cuotas es obligatoria")
        @Min(value = 1, message = "Mínimo 1 cuota")
        @Max(value = 60, message = "Máximo 60 cuotas")
        Integer cantidadCuotas,

        @NotNull(message = "La categoría es obligatoria")
        Integer categoriaId,

        @Size(max = 255)
        String descripcion,

        @NotNull
        @PastOrPresent
        LocalDate fechaCompra
) {}
