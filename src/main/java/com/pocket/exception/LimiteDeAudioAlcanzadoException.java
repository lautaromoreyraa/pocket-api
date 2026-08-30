package com.pocket.exception;

/**
 * El usuario agotó su cupo diario de procesamientos de audio.
 *
 * Solo aparece en la demo pública, donde el tope está activado. En el uso
 * normal de la app el límite es 0 (sin tope) y esta excepción nunca se lanza.
 */
public class LimiteDeAudioAlcanzadoException extends RuntimeException {
    public LimiteDeAudioAlcanzadoException(String mensaje) {
        super(mensaje);
    }
}
