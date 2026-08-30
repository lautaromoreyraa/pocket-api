package com.pocket.controller;

import com.pocket.dto.gasto.GastoResponse;
import com.pocket.dto.gastofijo.GastoFijoRequest;
import com.pocket.dto.gastofijo.GastoFijoResponse;
import com.pocket.dto.gastofijo.MontoRegistradoRequest;
import com.pocket.dto.gastofijo.RegistroFijoRequest;
import com.pocket.dto.gastofijo.ResumenFijosResponse;
import com.pocket.service.gastofijo.GastoFijoService;
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
@RequestMapping("/api/gastos-fijos")
@RequiredArgsConstructor
public class GastoFijoController {

    private final GastoFijoService gastoFijoService;

    @GetMapping
    public ResponseEntity<List<GastoFijoResponse>> listar() {
        return ResponseEntity.ok(gastoFijoService.listar());
    }

    @PostMapping
    public ResponseEntity<GastoFijoResponse> registrar(@Valid @RequestBody GastoFijoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gastoFijoService.registrar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GastoFijoResponse> editar(@PathVariable UUID id,
                                                    @Valid @RequestBody GastoFijoRequest request) {
        return ResponseEntity.ok(gastoFijoService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        gastoFijoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** El estado de cada plantilla dentro del período: qué está tildado y qué no. */
    @GetMapping("/resumen")
    public ResponseEntity<ResumenFijosResponse> resumen(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo) {
        return ResponseEntity.ok(gastoFijoService.resumenDelPeriodo(periodo));
    }

    /**
     * Tilda el fijo del mes: lo materializa como un `gasto` real.
     *
     * No hay endpoint para <b>destildar</b>, y la asimetría es a propósito: el
     * tilde es la existencia del gasto, así que borrarlo ya lo resuelve
     * DELETE /api/gastos/{id} con el `gastoId` que viene en el resumen. Es
     * además el mismo camino por el que el usuario puede borrarlo desde la
     * pantalla de movimientos, así que tener dos no agregaría nada.
     */
    @PostMapping("/{id}/registrar")
    public ResponseEntity<GastoResponse> registrarDelPeriodo(
            @PathVariable UUID id, @Valid @RequestBody RegistroFijoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gastoFijoService.registrarDelPeriodo(id, request));
    }

    /** Corrige cuánto se pagó este mes, sin tocar el resto del gasto. */
    @PutMapping("/{id}/registro")
    public ResponseEntity<GastoResponse> editarMontoDelPeriodo(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo,
            @Valid @RequestBody MontoRegistradoRequest request) {
        return ResponseEntity.ok(
                gastoFijoService.editarMontoDelPeriodo(id, periodo, request.monto()));
    }
}
