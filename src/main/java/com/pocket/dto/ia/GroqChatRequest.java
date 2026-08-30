package com.pocket.dto.ia;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request de chat de Groq, compatible con el formato de OpenAI.
 */
public record GroqChatRequest(
        String model,
        List<Mensaje> messages,

        /**
         * En cero: extraer gastos de un texto no es una tarea creativa. La misma
         * frase tiene que dar siempre los mismos montos.
         */
        Double temperature,

        @JsonProperty("response_format")
        FormatoRespuesta responseFormat
) {

    public record Mensaje(String role, String content) {}

    /** Le pide al modelo que devuelva JSON válido y nada más. */
    public record FormatoRespuesta(String type) {}

    public static GroqChatRequest deTexto(String modelo, String prompt) {
        return new GroqChatRequest(
                modelo,
                List.of(new Mensaje("user", prompt)),
                0.0,
                new FormatoRespuesta("json_object"));
    }
}
