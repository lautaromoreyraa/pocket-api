package com.pocket.exception;

import java.util.UUID;

public class CompraNoEncontradaException extends RecursoNoEncontradoException {
    public CompraNoEncontradaException(UUID id) {
        super("No se encontró la compra financiada " + id);
    }
}
