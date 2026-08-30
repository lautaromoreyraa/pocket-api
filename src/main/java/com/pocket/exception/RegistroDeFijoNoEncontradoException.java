package com.pocket.exception;

import java.time.YearMonth;
import java.util.UUID;

/** El gasto fijo existe, pero no se registró en ese período: no hay monto que corregir. */
public class RegistroDeFijoNoEncontradoException extends RecursoNoEncontradoException {
    public RegistroDeFijoNoEncontradoException(UUID gastoFijoId, YearMonth periodo) {
        super("El gasto fijo " + gastoFijoId + " no está registrado en el período " + periodo);
    }
}
