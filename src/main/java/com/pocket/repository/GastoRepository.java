package com.pocket.repository;

import com.pocket.domain.Gasto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
          AND (:incluirFijos  = true OR g.origen <> com.pocket.enumeration.OrigenGasto.FIJO)
        GROUP BY g.categoria
        ORDER BY SUM(g.monto) DESC
        """)
    List<Object[]> agruparPorCategoria(@Param("usuarioId") UUID usuarioId,
                                       @Param("desde") LocalDate desde,
                                       @Param("hasta") LocalDate hasta,
                                       @Param("credito") boolean credito,
                                       @Param("incluirCuotas") boolean incluirCuotas,
                                       @Param("incluirFijos") boolean incluirFijos);

    List<Gasto> findByCompraFinanciadaIdAndFechaImputacionGreaterThanEqual(UUID compraId,
                                                                           LocalDate desde);

    @Query("""
        SELECT MIN(g.fechaImputacion) FROM Gasto g
        WHERE g.usuario.id = :usuarioId
        """)
    LocalDate primerPeriodoConGastos(@Param("usuarioId") UUID usuarioId);

    @Query("""
        SELECT g FROM Gasto g
        JOIN FETCH g.categoria
        LEFT JOIN FETCH g.compraFinanciada
        WHERE g.usuario.id = :usuarioId
          AND g.fechaImputacion BETWEEN :desde AND :hasta
          AND (:credito = true  AND g.medioPago = com.pocket.enumeration.MedioPago.CREDITO
            OR :credito = false AND g.medioPago <> com.pocket.enumeration.MedioPago.CREDITO)
        ORDER BY g.fechaGasto DESC, g.fechaAlta DESC
        """)
    List<Gasto> findUltimosDelPeriodo(@Param("usuarioId") UUID usuarioId,
                                      @Param("desde") LocalDate desde,
                                      @Param("hasta") LocalDate hasta,
                                      @Param("credito") boolean credito,
                                      Pageable limite);

    @Query("""
        SELECT COALESCE(SUM(g.monto), 0) FROM Gasto g
        WHERE g.usuario.id = :usuarioId
          AND g.fechaImputacion BETWEEN :desde AND :hasta
          AND g.compraFinanciada IS NOT NULL
        """)
    BigDecimal totalCuotasDelPeriodo(@Param("usuarioId") UUID usuarioId,
                                     @Param("desde") LocalDate desde,
                                     @Param("hasta") LocalDate hasta);

    @Query("""
        SELECT g FROM Gasto g
        WHERE g.usuario.id = :usuarioId
          AND g.gastoFijo IS NOT NULL
          AND g.fechaGasto BETWEEN :desde AND :hasta
        """)
    List<Gasto> findFijosMaterializados(@Param("usuarioId") UUID usuarioId,
                                        @Param("desde") LocalDate desde,
                                        @Param("hasta") LocalDate hasta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Gasto g SET g.gastoFijo = NULL WHERE g.gastoFijo.id = :gastoFijoId")
    void desvincularDelFijo(@Param("gastoFijoId") UUID gastoFijoId);

    @Query("""
        SELECT g FROM Gasto g
        JOIN FETCH g.categoria
        WHERE g.gastoFijo.id = :gastoFijoId
          AND g.fechaGasto BETWEEN :desde AND :hasta
        """)
    Optional<Gasto> findRegistroDelFijo(@Param("gastoFijoId") UUID gastoFijoId,
                                        @Param("desde") LocalDate desde,
                                        @Param("hasta") LocalDate hasta);

    @Query("""
        SELECT COUNT(g) > 0 FROM Gasto g
        WHERE g.gastoFijo.id = :gastoFijoId
          AND g.fechaGasto BETWEEN :desde AND :hasta
        """)
    boolean existeRegistroDelFijo(@Param("gastoFijoId") UUID gastoFijoId,
                                  @Param("desde") LocalDate desde,
                                  @Param("hasta") LocalDate hasta);

    @Query("""
        SELECT DISTINCT g.fechaImputacion FROM Gasto g
        WHERE g.usuario.id = :usuarioId
        ORDER BY g.fechaImputacion
        """)
    List<LocalDate> periodosConGastos(@Param("usuarioId") UUID usuarioId);
}
