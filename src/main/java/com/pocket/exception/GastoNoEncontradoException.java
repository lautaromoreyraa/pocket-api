package com.pocket.exception;

import java.util.UUID;

public class GastoNoEncontradoException extends RecursoNoEncontradoException {
    public GastoNoEncontradoException(UUID id) {
        super("No se encontró el gasto " + id);
    }
}
