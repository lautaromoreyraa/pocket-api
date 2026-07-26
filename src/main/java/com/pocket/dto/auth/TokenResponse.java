package com.pocket.dto.auth;

public record TokenResponse(
        String token,
        String tipo,
        boolean cuentaVinculada
) {
    public static TokenResponse de(String token, boolean cuentaVinculada) {
        return new TokenResponse(token, "Bearer", cuentaVinculada);
    }
}
