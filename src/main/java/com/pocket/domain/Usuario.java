package com.pocket.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuario")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Usuario {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "device_uuid", nullable = false, unique = true, length = 64)
    private String deviceUuid;

    @Column(unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @CreatedDate
    @Column(name = "fecha_alta", nullable = false, updatable = false)
    private Instant fechaAlta;

    /** Un usuario está vinculado cuando completó el registro con email (RF-03). */
    public boolean tieneCuentaVinculada() {
        return email != null;
    }
}
