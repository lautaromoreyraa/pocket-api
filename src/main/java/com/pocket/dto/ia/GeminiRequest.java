package com.pocket.dto.ia;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record GeminiRequest(
        List<Content> contents,
        GenerationConfig generationConfig
) {

    public record Content(List<Part> parts) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Part(String text, InlineData inlineData) {}

    public record InlineData(String mimeType, String data) {}

    public record GenerationConfig(String responseMimeType) {}
}
