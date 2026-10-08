package com.pocket.dto.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * El JSON que devuelve el modelo, ya parseado. Distinto de GastoDetectado:
 * acá categoria y medioPago todavía son texto libre del modelo, sin resolver
 * contra la base ni con fallback.
 *
 * La forma es la misma para todos los proveedores porque la define el prompt,
 * no la API: por eso el tipo no lleva el nombre de ninguno.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RespuestaModeloIA(String transcripcion, List<GastoModelo> gastos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GastoModelo(
            BigDecimal monto,
            String categoria,
            String descripcion,
            String medioPago,

            /** Null si el audio no mencionó cuotas (RF-05). */
            Integer cantidadCuotas
    ) {}
}
