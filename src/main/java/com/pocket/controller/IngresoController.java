package com.pocket.controller;

import com.pocket.dto.ingreso.IngresoRequest;
import com.pocket.dto.ingreso.IngresoResponse;
import com.pocket.service.ingreso.IngresoService;
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
@RequestMapping("/api/ingresos")
@RequiredArgsConstructor
public class IngresoController {

    private final IngresoService ingresoService;

    @PostMapping
    public ResponseEntity<IngresoResponse> registrar(@Valid @RequestBody IngresoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ingresoService.registrar(request));
    }

    @GetMapping
    public ResponseEntity<List<IngresoResponse>> listar(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo) {
        return ResponseEntity.ok(ingresoService.listarDelPeriodo(periodo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        ingresoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
