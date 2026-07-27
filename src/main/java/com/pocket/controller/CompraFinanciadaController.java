package com.pocket.controller;

import com.pocket.dto.compra.CompraEdicionRequest;
import com.pocket.dto.compra.CompraFinanciadaRequest;
import com.pocket.dto.compra.CompraFinanciadaResponse;
import com.pocket.dto.resumen.CuotaEnCursoResponse;
import com.pocket.service.compra.CompraFinanciadaService;
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
@RequestMapping("/api/compras")
@RequiredArgsConstructor
public class CompraFinanciadaController {

    private final CompraFinanciadaService compraService;

    @PostMapping
    public ResponseEntity<CompraFinanciadaResponse> registrar(
            @Valid @RequestBody CompraFinanciadaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(compraService.registrar(request));
    }

    @GetMapping("/en-curso")
    public ResponseEntity<List<CuotaEnCursoResponse>> enCurso(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo) {
        return ResponseEntity.ok(compraService.cuotasEnCurso(periodo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CompraFinanciadaResponse> editar(
            @PathVariable UUID id, @Valid @RequestBody CompraEdicionRequest request) {
        return ResponseEntity.ok(compraService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        compraService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
