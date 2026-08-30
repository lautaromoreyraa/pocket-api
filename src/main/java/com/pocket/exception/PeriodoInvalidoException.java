package com.pocket.exception;

/** El período pedido no puede procesarse: 400, es un dato del cliente. */
public class PeriodoInvalidoException extends RuntimeException {
    public PeriodoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
