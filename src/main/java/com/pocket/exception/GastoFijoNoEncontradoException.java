package com.pocket.exception;

import java.util.UUID;

public class GastoFijoNoEncontradoException extends RecursoNoEncontradoException {
    public GastoFijoNoEncontradoException(UUID id) {
        super("No se encontró el gasto fijo " + id);
    }
}
