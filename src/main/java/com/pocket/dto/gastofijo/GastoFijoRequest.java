package com.pocket.dto.gastofijo;

import com.pocket.enumeration.MedioPago;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Alta y edición de una plantilla de gasto fijo.
 *
 * El frontend manda el objeto entero que tiene en pantalla, con `id` y
 * `categoriaNombre` incluidos; Spring Boot ignora los campos de más por
 * default, así que no hace falta que arme un cuerpo distinto.
 */
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

        /** Tope 28: con 29, 30 o 31 habría que decidir qué pasa en febrero. */
        @NotNull(message = "El día del mes es obligatorio")
        @Min(value = 1, message = "El día del mes va de 1 a 28")
        @Max(value = 28, message = "El día del mes va de 1 a 28")
        Integer diaDelMes,

        /**
         * Null se toma como activo.
         *
         * En el alta el frontend directamente no manda el campo: crear una
         * plantilla ya pausada no tiene sentido —estarías definiendo algo que
         * pedís que no se espere—, y pausar es una acción posterior sobre algo
         * que ya existe. En la edición sí viaja, y ahí manda lo que diga.
         */
        Boolean activo
) {

    public boolean activoOPorDefecto() {
        return activo == null || activo;
    }
}
