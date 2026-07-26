package com.pocket.security.jwt;

import com.pocket.domain.Usuario;

import java.util.UUID;

public interface JwtService {

    String generar(Usuario usuario);

    boolean esValido(String token);

    UUID extraerUsuarioId(String token);
}
