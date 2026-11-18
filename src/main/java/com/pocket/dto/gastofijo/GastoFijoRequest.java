package com.pocket.dto.gastofijo;

import com.pocket.enumeration.MedioPago;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record GastoFijoRequest(

        @Size(max = 255)
        String descripcion,

        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        @Digits(integer = 10, fraction = 2)
        BigDecimal monto,

        @NotNull(message = "La categoría es obligatoria")
        Integer categoriaId,

        @NotNull(message = "El medio de pago es obligatorio")
        MedioPago medioPago,

        @NotNull(message = "El día del mes es obligatorio")
        @Min(value = 1, message = "El día del mes va de 1 a 28")
        @Max(value = 28, message = "El día del mes va de 1 a 28")
        Integer diaDelMes,

        Boolean activo
) {

    public boolean activoOPorDefecto() {
        return activo == null || activo;
    }
}
