package com.pocket.dto.ia;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GroqChatRequest(
        String model,
        List<Mensaje> messages,

        Double temperature,

        @JsonProperty("response_format")
        FormatoRespuesta responseFormat
) {

    public record Mensaje(String role, String content) {}

    public record FormatoRespuesta(String type) {}

    public static GroqChatRequest deTexto(String modelo, String prompt) {
        return new GroqChatRequest(
                modelo,
                List.of(new Mensaje("user", prompt)),
                0.0,
                new FormatoRespuesta("json_object"));
    }
}
