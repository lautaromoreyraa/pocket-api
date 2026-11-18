package com.pocket.dto.ia;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroqChatResponse(List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Mensaje message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mensaje(String content) {}
}
