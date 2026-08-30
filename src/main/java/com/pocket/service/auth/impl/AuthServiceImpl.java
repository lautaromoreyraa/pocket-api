package com.pocket.service.auth.impl;

import com.pocket.domain.Usuario;
import com.pocket.dto.auth.DispositivoRequest;
import com.pocket.dto.auth.TokenResponse;
import com.pocket.exception.NoAutenticadoException;
import com.pocket.repository.UsuarioRepository;
import com.pocket.security.jwt.JwtService;
import com.pocket.event.UsuarioCreado;
import com.pocket.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventos;

    @Override
    @Transactional
    public TokenResponse identificar(DispositivoRequest request) {
        Usuario usuario = usuarioRepository.findByDeviceUuid(request.deviceUuid())
                .orElseGet(() -> crear(request.deviceUuid()));

        String token = jwtService.generar(usuario);
        return TokenResponse.de(token, usuario.tieneCuentaVinculada());
    }

    /**
     * Alta del usuario nuevo. Si dos requests con el mismo deviceUuid corren en
     * paralelo, el UNIQUE de la tabla hace fallar al segundo con
     * DataIntegrityViolationException: en ese caso el usuario ya existe, así que
     * lo recuperamos en vez de propagar el error.
     */
    private Usuario crear(String deviceUuid) {
        try {
            Usuario nuevo = usuarioRepository.save(Usuario.builder()
                    .deviceUuid(deviceUuid)
                    .build());

            // Lo que escuche esto corre después del commit y en su propia
            // transacción: sembrar la demo acá adentro haría que un save fallido
            // marcara esta transacción como rollback-only y tumbara el alta con
            // él, que es exactamente lo que no puede pasar en la identificación.
            eventos.publishEvent(new UsuarioCreado(nuevo.getId()));

            return nuevo;
        } catch (DataIntegrityViolationException e) {
            return usuarioRepository.findByDeviceUuid(deviceUuid)
                    .orElseThrow(() -> e);
        }
    }

    @Override
    public Usuario actual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof Usuario usuario)) {
            throw new NoAutenticadoException(
                    "No hay un usuario autenticado en el contexto de seguridad.");
        }
        return usuario;
    }
}
