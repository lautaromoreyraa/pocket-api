package com.pocket.dto.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lo que devuelve el endpoint de transcripción de Groq.
 *
 * Es todo lo que hace ese modelo: pasar el audio a texto. No interpreta, no
 * categoriza y no sabe qué es un gasto — y eso es exactamente lo que lo vuelve
 * util como evidencia contra montos fabricados.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroqTranscripcionResponse(String text) {}
