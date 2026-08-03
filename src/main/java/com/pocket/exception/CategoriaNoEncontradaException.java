package com.pocket.exception;

public class CategoriaNoEncontradaException extends RecursoNoEncontradoException {
    public CategoriaNoEncontradaException(Integer id) {
        super("No se encontró la categoría " + id);
    }
}
