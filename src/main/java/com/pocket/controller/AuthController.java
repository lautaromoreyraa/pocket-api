package com.pocket.controller;

import com.pocket.dto.auth.DispositivoRequest;
import com.pocket.dto.auth.TokenResponse;
import com.pocket.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/dispositivo")
    public ResponseEntity<TokenResponse> identificar(@Valid @RequestBody DispositivoRequest request) {
        return ResponseEntity.ok(authService.identificar(request));
    }
}
