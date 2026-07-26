package com.pocket.repository;

import com.pocket.domain.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GastoRepository extends JpaRepository<Gasto, UUID> {

    Optional<Gasto> findByIdempotencyKey(UUID idempotencyKey);

    @Query("""
        SELECT g FROM Gasto g
        JOIN FETCH g.categoria
        WHERE g.usuario.id = :usuarioId
          AND g.fechaImputacion BETWEEN :desde AND :hasta
          AND (:credito = true  AND g.medioPago = com.pocket.enumeration.MedioPago.CREDITO
            OR :credito = false AND g.medioPago <> com.pocket.enumeration.MedioPago.CREDITO)
        ORDER BY g.fechaGasto DESC
        """)
    List<Gasto> findDelPeriodo(@Param("usuarioId") UUID usuarioId,
                               @Param("desde") LocalDate desde,
                               @Param("hasta") LocalDate hasta,
                               @Param("credito") boolean credito);

    @Query("""
        SELECT COALESCE(SUM(g.monto), 0) FROM Gasto g
        WHERE g.usuario.id = :usuarioId
          AND g.fechaImputacion BETWEEN :desde AND :hasta
          AND (:credito IS NULL
            OR :credito = true  AND g.medioPago = com.pocket.enumeration.MedioPago.CREDITO
            OR :credito = false AND g.medioPago <> com.pocket.enumeration.MedioPago.CREDITO)
        """)
    BigDecimal totalDelPeriodo(@Param("usuarioId") UUID usuarioId,
                               @Param("desde") LocalDate desde,
                               @Param("hasta") LocalDate hasta,
                               @Param("credito") Boolean credito);

    @Query("""
        SELECT g.categoria, COUNT(g), SUM(g.monto) FROM Gasto g
        WHERE g.usuario.id = :usuarioId
          AND g.fechaImputacion BETWEEN :desde AND :hasta
          AND (:credito = true  AND g.medioPago = com.pocket.enumeration.MedioPago.CREDITO
            OR :credito = false AND g.medioPago <> com.pocket.enumeration.MedioPago.CREDITO)
          AND (:incluirCuotas = true OR g.compraFinanciada IS NULL)
        GROUP BY g.categoria
        ORDER BY SUM(g.monto) DESC
        """)
    List<Object[]> agruparPorCategoria(@Param("usuarioId") UUID usuarioId,
                                       @Param("desde") LocalDate desde,
                                       @Param("hasta") LocalDate hasta,
                                       @Param("credito") boolean credito,
                                       @Param("incluirCuotas") boolean incluirCuotas);

    List<Gasto> findByCompraFinanciadaIdAndFechaImputacionGreaterThanEqual(UUID compraId,
                                                                           LocalDate desde);

    @Query("""
        SELECT MIN(g.fechaImputacion) FROM Gasto g
        WHERE g.usuario.id = :usuarioId
        """)
    LocalDate primerPeriodoConGastos(@Param("usuarioId") UUID usuarioId);
}
