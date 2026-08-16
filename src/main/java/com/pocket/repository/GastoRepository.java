package com.pocket.repository;

import com.pocket.domain.Gasto;
import org.springframework.data.domain.Pageable;
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

    /**
     * Los primeros N del período, para el preview de movimientos del resumen.
     * Mismo criterio y mismo orden que findDelPeriodo: si al tocar "Ver más"
     * apareciera otro conjunto, el preview estaría mintiendo.
     */
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

    /**
     * Suma de las cuotas que se imputan al período: el "Ya comprometido" de la
     * pestaña de crédito. Solo cuotas, o sea gastos con compra financiada
     * detrás; un gasto de crédito sin cuotas no es un compromiso a futuro.
     */
    @Query("""
        SELECT COALESCE(SUM(g.monto), 0) FROM Gasto g
        WHERE g.usuario.id = :usuarioId
          AND g.fechaImputacion BETWEEN :desde AND :hasta
          AND g.compraFinanciada IS NOT NULL
        """)
    BigDecimal totalCuotasDelPeriodo(@Param("usuarioId") UUID usuarioId,
                                     @Param("desde") LocalDate desde,
                                     @Param("hasta") LocalDate hasta);

    /** Meses con al menos un gasto, del más viejo al más nuevo. Alimenta el
     *  selector de la pestaña Histórico. */
    @Query("""
        SELECT DISTINCT g.fechaImputacion FROM Gasto g
        WHERE g.usuario.id = :usuarioId
        ORDER BY g.fechaImputacion
        """)
    List<LocalDate> periodosConGastos(@Param("usuarioId") UUID usuarioId);
}
