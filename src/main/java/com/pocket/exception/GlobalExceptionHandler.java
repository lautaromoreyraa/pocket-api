package com.pocket.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.de(404, "No encontrado", e.getMessage()));
    }

    @ExceptionHandler(AudioNoComprendidoException.class)
    public ResponseEntity<ErrorResponse> audioNoComprendido(AudioNoComprendidoException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.de(422, "Audio no comprendido", e.getMessage()));
    }

    @ExceptionHandler(CuotasInvalidasException.class)
    public ResponseEntity<ErrorResponse> cuotasInvalidas(CuotasInvalidasException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.de(400, "Cuotas inválidas", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException e) {
        Map<String, String> detalles = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(err -> detalles.put(err.getField(), err.getDefaultMessage()));

        return ResponseEntity.badRequest().body(new ErrorResponse(
                Instant.now(), 400, "Datos inválidos",
                "Revisá los campos marcados", detalles));
    }
}
