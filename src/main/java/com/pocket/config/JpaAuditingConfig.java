package com.pocket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Habilita la auditoría automática de JPA.
 *
 * Con esto, los campos anotados con @CreatedDate se completan solos al
 * persistir la entidad: no hace falta setear fechaAlta en ningún servicio.
 * Las entidades que lo usan llevan @EntityListeners(AuditingEntityListener.class).
 *
 * Si en el futuro se agregan campos de modificación, basta con anotarlos
 * con @LastModifiedDate y Spring los mantiene actualizados.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
