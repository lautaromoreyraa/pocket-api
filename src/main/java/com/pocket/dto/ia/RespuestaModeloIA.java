package com.pocket.dto.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RespuestaModeloIA(String transcripcion, List<GastoModelo> gastos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GastoModelo(
            BigDecimal monto,
            String categoria,
            String descripcion,
            String medioPago,

            Integer cantidadCuotas
    ) {}
}
