package com.pocket.dto.turnstile;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lo que devuelve el siteverify de Cloudflare Turnstile.
 *
 * Solo decide {@code success}; los códigos de error van al log, porque
 * distinguen un token vencido o reusado de una clave mal cargada.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RespuestaDeTurnstile(
        boolean success,
        @JsonProperty("error-codes") List<String> codigosDeError
) {}
