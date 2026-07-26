package com.pocket.controller;

import com.pocket.dto.cotizacion.CotizacionResponse;
import com.pocket.service.cotizacion.CotizacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cotizacion")
@RequiredArgsConstructor
public class CotizacionController {

    private final CotizacionService cotizacionService;

    @GetMapping("/blue")
    public ResponseEntity<CotizacionResponse> blue() {
        return ResponseEntity.ok(cotizacionService.obtenerBlue());
    }
}
