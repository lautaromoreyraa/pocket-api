package com.pocket.security.jwt.impl;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Usuario;
import com.pocket.security.jwt.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * El secreto de firma llega desde application-local.properties en desarrollo
 * y desde una variable de entorno en producción. Nunca se commitea.
 */
@Service
public class JwtServiceImpl implements JwtService {

    private final PocketProperties props;
    private final SecretKey key;

    public JwtServiceImpl(PocketProperties props) {
        this.props = props;
        this.key = derivarClave(props.getSecurity().getJwt().getSecret());
    }

    private SecretKey derivarClave(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "El secreto JWT debe tener al menos 32 bytes (256 bits) para HS256. " +
                    "Revisá pocket.security.jwt.secret en application-local.properties.");
        }
        try {
            return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        } catch (WeakKeyException e) {
            throw new IllegalStateException(
                    "El secreto JWT es demasiado débil para HS256. " +
                    "Revisá pocket.security.jwt.secret en application-local.properties.", e);
        }
    }

    @Override
    public String generar(Usuario usuario) {
        PocketProperties.Security.Jwt jwt = props.getSecurity().getJwt();
        Instant ahora = Instant.now();
        Instant expiracion = ahora.plus(jwt.getExpiracionDias(), ChronoUnit.DAYS);

        return Jwts.builder()
                .subject(usuario.getId().toString())
                .issuer(jwt.getEmisor())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expiracion))
                .signWith(key)
                .compact();
    }

    @Override
    public boolean esValido(String token) {
        try {
            parsear(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public UUID extraerUsuarioId(String token) {
        return UUID.fromString(parsear(token).getSubject());
    }

    private Claims parsear(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.getSecurity().getJwt().getEmisor())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
