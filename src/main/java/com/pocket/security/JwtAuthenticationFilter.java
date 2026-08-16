package com.pocket.security;

import com.pocket.repository.UsuarioRepository;
import com.pocket.security.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * Lee el JWT del header Authorization y puebla el SecurityContext.
 * En mobile no hay cookies, por eso el token viaja siempre en el header.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO_BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // A propósito en DEBUG y no TRACE: es la primera pista cuando un
        // request se cae antes de llegar al controller (ver diagnóstico de
        // /api/audio devolviendo 401 fantasma con archivos grandes: el
        // problema terminó siendo el forward a /error, no este filtro, pero
        // sin este log confirmar que el filtro SÍ corría costó mucho más).
        log.debug("JWT filter: método={}, path={}, dispatcherType={}, tieneAuthorizationHeader={}",
                request.getMethod(), request.getRequestURI(), request.getDispatcherType(),
                request.getHeader(HttpHeaders.AUTHORIZATION) != null);

        String token = extraerToken(request);

        if (token != null
                && SecurityContextHolder.getContext().getAuthentication() == null
                && jwtService.esValido(token)) {
            autenticar(request, token);
            log.debug("JWT filter: autenticado OK para path={}", request.getRequestURI());
        } else if (token != null) {
            log.debug("JWT filter: token presente pero no autenticó (inválido, o ya había contexto) para path={}",
                    request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    private void autenticar(HttpServletRequest request, String token) {
        UUID usuarioId = jwtService.extraerUsuarioId(token);
        usuarioRepository.findById(usuarioId).ifPresent(usuario -> {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(usuario, null, Collections.emptyList());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        });
    }

    private String extraerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIJO_BEARER)) {
            return header.substring(PREFIJO_BEARER.length());
        }
        return null;
    }
}
