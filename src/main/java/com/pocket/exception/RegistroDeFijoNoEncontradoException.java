package com.pocket.exception;

import java.time.YearMonth;
import java.util.UUID;

public class RegistroDeFijoNoEncontradoException extends RecursoNoEncontradoException {
    public RegistroDeFijoNoEncontradoException(UUID gastoFijoId, YearMonth periodo) {
        super("El gasto fijo " + gastoFijoId + " no está registrado en el período " + periodo);
    }
}
