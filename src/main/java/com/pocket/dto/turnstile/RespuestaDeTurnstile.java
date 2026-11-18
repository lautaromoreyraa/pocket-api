package com.pocket.dto.turnstile;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RespuestaDeTurnstile(
        boolean success,
        @JsonProperty("error-codes") List<String> codigosDeError
) {}
