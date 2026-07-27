package com.pocket.exception;

import java.util.UUID;

public class IngresoNoEncontradoException extends RecursoNoEncontradoException {
    public IngresoNoEncontradoException(UUID id) {
        super("No se encontró el ingreso " + id);
    }
}
