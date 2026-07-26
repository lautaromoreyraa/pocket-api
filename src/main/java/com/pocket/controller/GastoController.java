package com.pocket.controller;

import com.pocket.dto.gasto.GastoRequest;
import com.pocket.dto.gasto.GastoResponse;
import com.pocket.enumeration.OrigenGasto;
import com.pocket.service.gasto.GastoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/gastos")
@RequiredArgsConstructor
public class GastoController {

    private final GastoService gastoService;

    @PostMapping
    public ResponseEntity<GastoResponse> registrar(@Valid @RequestBody GastoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gastoService.registrar(request, OrigenGasto.MANUAL));
    }

    @PostMapping("/lote")
    public ResponseEntity<List<GastoResponse>> confirmarLote(@Valid @RequestBody List<GastoRequest> requests) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gastoService.registrarLote(requests, OrigenGasto.VOZ));
    }

    @GetMapping
    public ResponseEntity<List<GastoResponse>> listar(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo,
            @RequestParam(defaultValue = "false") boolean credito) {
        return ResponseEntity.ok(gastoService.listarDelPeriodo(periodo, credito));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GastoResponse> editar(@PathVariable UUID id,
                                                @Valid @RequestBody GastoRequest request) {
        return ResponseEntity.ok(gastoService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        gastoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
