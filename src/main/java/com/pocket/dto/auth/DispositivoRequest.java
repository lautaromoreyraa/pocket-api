package com.pocket.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record DispositivoRequest(

        @NotBlank(message = "El identificador del dispositivo es obligatorio")
        String deviceUuid,

        /**
         * Token del widget de Turnstile. Solo se mira cuando el dispositivo es
         * nuevo: quien vuelve ya pasó la verificación al darse de alta.
         */
        String turnstileToken
) {}
