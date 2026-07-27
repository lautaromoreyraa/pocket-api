package com.pocket.repository;

import com.pocket.domain.CompraFinanciada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompraFinanciadaRepository extends JpaRepository<CompraFinanciada, UUID> {

    Optional<CompraFinanciada> findByIdempotencyKey(UUID idempotencyKey);

    List<CompraFinanciada> findByUsuarioId(UUID usuarioId);

    @Query("""
        SELECT DISTINCT c FROM CompraFinanciada c
        JOIN c.cuotas g
        WHERE c.usuario.id = :usuarioId
          AND g.fechaImputacion >= :desde
        ORDER BY c.fechaCompra DESC
        """)
    List<CompraFinanciada> findConCuotasPendientes(@Param("usuarioId") UUID usuarioId,
                                                   @Param("desde") LocalDate desde);
}
