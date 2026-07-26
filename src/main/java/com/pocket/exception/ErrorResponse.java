package com.pocket.exception;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        Instant momento,
        int estado,
        String error,
        String mensaje,
        Map<String, String> detalles
) {
    public static ErrorResponse de(int estado, String error, String mensaje) {
        return new ErrorResponse(Instant.now(), estado, error, mensaje, Map.of());
    }
}
