package com.pocket.dto.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que el modelo devuelve como texto (el JSON dentro de GeminiResponse),
 * ya parseado. Distinto de GastoDetectado: acá categoria y medioPago todavía
 * son texto libre del modelo, sin resolver contra la base ni con fallback.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiRespuestaModelo(String transcripcion, List<GastoModelo> gastos) {

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
