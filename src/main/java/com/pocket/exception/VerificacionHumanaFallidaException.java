package com.pocket.exception;

/**
 * El alta de un dispositivo nuevo no pasó la verificación de Turnstile: falta
 * el token, venció, ya se usó o Cloudflare no pudo confirmarlo.
 */
public class VerificacionHumanaFallidaException extends RuntimeException {
    public VerificacionHumanaFallidaException(String mensaje) {
        super(mensaje);
    }
}
