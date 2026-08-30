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

    /**
     * Agrupado por categoría del período: (categoría, cantidad, suma).
     *
     * Los dos flags existen porque el mismo agregado responde dos preguntas
     * distintas y con reglas distintas:
     *
     * <ul>
     *   <li><b>Cuánta plata</b> se fue en cada categoría → con todo incluido.
     *       Una cuota y un fijo son plata que salió.</li>
     *   <li><b>Cuántas veces se repitió</b> una categoría, que es lo que
     *       alimenta la detección de gasto hormiga → sin cuotas ni fijos
     *       (RN-02). Un gasto hormiga es un gasto chico, repetido y evitable;
     *       la luz y la cuota de la heladera no son ninguna de las tres cosas.</li>
     * </ul>
     */
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

    /**
     * Los gastos ya materializados desde una plantilla de gasto fijo en el
     * período. Es lo que decide qué fijos aparecen tildados en la pantalla.
     *
     * Se filtra por <b>fechaGasto</b>, no por fechaImputacion, y es la única
     * consulta del proyecto que lo hace. La razón: la pestaña Fijos habla del
     * mes en el que pagás, y un fijo de crédito se paga en agosto aunque se
     * impute a septiembre (RN-03). Filtrar por imputación dejaría al fijo de
     * crédito sin tildar el mes que lo pagaste, que es justo cuando querés verlo.
     */
    @Query("""
        SELECT g FROM Gasto g
        WHERE g.usuario.id = :usuarioId
          AND g.gastoFijo IS NOT NULL
          AND g.fechaGasto BETWEEN :desde AND :hasta
        """)
    List<Gasto> findFijosMaterializados(@Param("usuarioId") UUID usuarioId,
                                        @Param("desde") LocalDate desde,
                                        @Param("hasta") LocalDate hasta);

    /**
     * Suelta los gastos de una plantilla que se va a borrar, sin tocarlos en
     * nada más: la baja de la plantilla no borra los meses ya pagados (RF-44).
     *
     * Es lo que haría un ON DELETE SET NULL, pero explícito, porque MySQL no
     * admite esa acción referencial sobre una columna que además participa de un
     * CHECK (ver V4).
     */
    // flush + clear no son decorado: un UPDATE masivo esquiva el persistence
    // context, así que sin `clearAutomatically` los Gasto que ya estuvieran en
    // la sesión seguirían apuntando a la plantilla que se borra un renglón
    // después, y el flush estalla con un TransientObjectException.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Gasto g SET g.gastoFijo = NULL WHERE g.gastoFijo.id = :gastoFijoId")
    void desvincularDelFijo(@Param("gastoFijoId") UUID gastoFijoId);

    /** El gasto que materializa a esta plantilla en el período, si ya se tildó. */
    @Query("""
        SELECT g FROM Gasto g
        JOIN FETCH g.categoria
        WHERE g.gastoFijo.id = :gastoFijoId
          AND g.fechaGasto BETWEEN :desde AND :hasta
        """)
    Optional<Gasto> findRegistroDelFijo(@Param("gastoFijoId") UUID gastoFijoId,
                                        @Param("desde") LocalDate desde,
                                        @Param("hasta") LocalDate hasta);

    /** Si esta plantilla ya se registró en el período. El UNIQUE de la tabla es
     *  la red de seguridad; esto es lo que permite responder un 409 con mensaje. */
    @Query("""
        SELECT COUNT(g) > 0 FROM Gasto g
        WHERE g.gastoFijo.id = :gastoFijoId
          AND g.fechaGasto BETWEEN :desde AND :hasta
        """)
    boolean existeRegistroDelFijo(@Param("gastoFijoId") UUID gastoFijoId,
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
