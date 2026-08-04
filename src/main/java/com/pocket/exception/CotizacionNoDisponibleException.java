package com.pocket.exception;

/**
 * Nunca hubo una cotización guardada y la API externa tampoco respondió
 * (RF-40, RNF-04). Distinta de RecursoNoEncontradoException: no es que el
 * dato no exista para este usuario, es que el servicio no está disponible.
 */
public class CotizacionNoDisponibleException extends RuntimeException {
    public CotizacionNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
