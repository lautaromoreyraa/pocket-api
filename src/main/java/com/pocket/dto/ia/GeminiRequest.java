package com.pocket.dto.ia;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Contrato de request de la API de Gemini (generateContent), no el nuestro.
 * Multimodal en una sola llamada: el audio va inline junto con el prompt,
 * sin transcripción previa.
 */
public record GeminiRequest(
        List<Content> contents,
        GenerationConfig generationConfig
) {

    public record Content(List<Part> parts) {}

    /** Una parte es texto O audio inline, nunca las dos cosas. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Part(String text, InlineData inlineData) {}

    public record InlineData(String mimeType, String data) {}

    public record GenerationConfig(String responseMimeType) {}
}
