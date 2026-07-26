package com.pocket.controller;

import com.pocket.dto.resumen.ResumenResponse;
import com.pocket.service.resumen.ResumenService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/resumen")
@RequiredArgsConstructor
public class ResumenController {

    private final ResumenService resumenService;

    @GetMapping
    public ResponseEntity<ResumenResponse> obtener(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo,
            @RequestParam(defaultValue = "false") boolean credito) {
        return ResponseEntity.ok(resumenService.armar(periodo, credito));
    }
}
