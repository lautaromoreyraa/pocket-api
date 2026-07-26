package com.pocket.security.jwt.impl;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Usuario;
import com.pocket.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final PocketProperties props;

    @Override
    public String generar(Usuario usuario) {
        // TODO: Jwts.builder() con subject = usuario.getId(), emisor y expiración
        //       tomados de props.getSecurity().getJwt()
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public boolean esValido(String token) {
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public UUID extraerUsuarioId(String token) {
        throw new UnsupportedOperationException("Pendiente de implementar");
    }
}
