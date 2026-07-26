package com.pocket.service.auth.impl;

import com.pocket.domain.Usuario;
import com.pocket.dto.auth.DispositivoRequest;
import com.pocket.dto.auth.TokenResponse;
import com.pocket.repository.UsuarioRepository;
import com.pocket.security.jwt.JwtService;
import com.pocket.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;

    @Override
    @Transactional
    public TokenResponse identificar(DispositivoRequest request) {
        // TODO: buscar por deviceUuid, crear si no existe, emitir JWT.
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public Usuario actual() {
        // TODO: leer el usuario del SecurityContext poblado por el filtro JWT.
        throw new UnsupportedOperationException("Pendiente de implementar");
    }
}
