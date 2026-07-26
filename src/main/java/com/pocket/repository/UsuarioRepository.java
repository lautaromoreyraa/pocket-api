package com.pocket.repository;

import com.pocket.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByDeviceUuid(String deviceUuid);

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
