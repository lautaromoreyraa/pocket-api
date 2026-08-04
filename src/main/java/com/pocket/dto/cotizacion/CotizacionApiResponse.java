package com.pocket.dto.cotizacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Contrato de la API externa de cotización (RF-38), no el nuestro.
 * `@JsonIgnoreProperties` porque la respuesta trae más campos (moneda, casa,
 * nombre, fechaActualizacion) que no nos interesan; solo nos importan compra
 * y venta. Guardamos con nuestro propio reloj (Instant.now()), no con el
 * timestamp de la API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CotizacionApiResponse(
        BigDecimal compra,
        BigDecimal venta
) {}
