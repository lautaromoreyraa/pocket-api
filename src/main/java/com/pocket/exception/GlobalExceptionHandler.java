package com.pocket.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
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

    @ExceptionHandler(LimiteDeAudioAlcanzadoException.class)
    public ResponseEntity<ErrorResponse> limiteDeAudio(LimiteDeAudioAlcanzadoException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.de(429, "Límite alcanzado", e.getMessage()));
    }

    @ExceptionHandler(NoAutenticadoException.class)
    public ResponseEntity<ErrorResponse> noAutenticado(NoAutenticadoException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.de(401, "No autenticado", e.getMessage()));
    }

    @ExceptionHandler(OperacionNoPermitidaException.class)
    public ResponseEntity<ErrorResponse> operacionNoPermitida(OperacionNoPermitidaException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.de(409, "Operación no permitida", e.getMessage()));
    }

    @ExceptionHandler(CuotasInvalidasException.class)
    public ResponseEntity<ErrorResponse> cuotasInvalidas(CuotasInvalidasException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.de(400, "Cuotas inválidas", e.getMessage()));
    }

    @ExceptionHandler(PeriodoInvalidoException.class)
    public ResponseEntity<ErrorResponse> periodoInvalido(PeriodoInvalidoException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.de(400, "Período inválido", e.getMessage()));
    }

    @ExceptionHandler(CotizacionNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> cotizacionNoDisponible(CotizacionNoDisponibleException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.de(503, "Cotización no disponible", e.getMessage()));
    }

    @ExceptionHandler(ArchivoInvalidoException.class)
    public ResponseEntity<ErrorResponse> archivoInvalido(ArchivoInvalidoException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.de(400, "Archivo inválido", e.getMessage()));
    }

    /**
     * Falla una integración externa sin manejo específico propio (Gemini,
     * la API de cotización si algún día se llama fuera de su fallback, etc.):
     * 502, no 500. Sin este handler la excepción quedaba sin capturar y
     * dependía del forward interno a /error para no filtrar (ver
     * SecurityConfig: JwtAuthenticationFilter, como todo OncePerRequestFilter,
     * no corre en dispatches de tipo ERROR por default, así que /error
     * necesita estar en permitAll para no devolver un 401 fantasma).
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse> servicioExternoNoDisponible(RestClientException e) {
        log.warn("Falló una llamada a un servicio externo: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.de(502, "Servicio externo no disponible",
                        "No se pudo completar el pedido a un servicio externo. Probá de nuevo en un momento."));
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
