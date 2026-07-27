package com.pocket.exception;

/** No hay un usuario autenticado en el contexto de seguridad (RF-02). */
public class NoAutenticadoException extends RuntimeException {
    public NoAutenticadoException(String mensaje) {
        super(mensaje);
    }
}
