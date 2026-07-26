package com.pocket.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record DispositivoRequest(

        @NotBlank(message = "El identificador del dispositivo es obligatorio")
        String deviceUuid
) {}
