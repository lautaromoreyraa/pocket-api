package com.pocket.repository;

import com.pocket.domain.Ingreso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface IngresoRepository extends JpaRepository<Ingreso, UUID> {

    List<Ingreso> findByUsuarioIdAndPeriodo(UUID usuarioId, LocalDate periodo);

    @Query("""
        SELECT COALESCE(SUM(i.monto), 0) FROM Ingreso i
        WHERE i.usuario.id = :usuarioId AND i.periodo = :periodo
        """)
    BigDecimal totalDelPeriodo(@Param("usuarioId") UUID usuarioId,
                               @Param("periodo") LocalDate periodo);

    boolean existsByUsuarioIdAndPeriodo(UUID usuarioId, LocalDate periodo);
}
