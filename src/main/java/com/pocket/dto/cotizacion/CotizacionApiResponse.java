package com.pocket.dto.cotizacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CotizacionApiResponse(
        BigDecimal compra,
        BigDecimal venta
) {}
