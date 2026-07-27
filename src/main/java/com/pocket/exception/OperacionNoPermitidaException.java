package com.pocket.exception;

/**
 * La operación choca con el estado actual del recurso (no es un problema de
 * datos ni de permisos). Se mapea a 409 Conflict.
 */
public class OperacionNoPermitidaException extends RuntimeException {
    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
