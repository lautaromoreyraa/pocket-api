package com.pocket.exception;

/** Audio vacío o de un content type que no es audio. */
public class ArchivoInvalidoException extends RuntimeException {
    public ArchivoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
