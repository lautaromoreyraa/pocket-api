package com.pocket.repository;

import com.pocket.domain.GastoFijo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GastoFijoRepository extends JpaRepository<GastoFijo, UUID> {

    /**
     * Las plantillas del usuario, activas y pausadas: la pantalla muestra las
     * dos cosas, en secciones distintas.
     *
     * El orden es por día del mes porque la pregunta que responde la pantalla es
     * "qué me falta pagar", y eso se lee en orden de vencimiento.
     */
    @Query("""
        SELECT f FROM GastoFijo f
        JOIN FETCH f.categoria
        WHERE f.usuario.id = :usuarioId
        ORDER BY f.diaDelMes, f.descripcion
        """)
    List<GastoFijo> findDelUsuario(@Param("usuarioId") UUID usuarioId);
}
