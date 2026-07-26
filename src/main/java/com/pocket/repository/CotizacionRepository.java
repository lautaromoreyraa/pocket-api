package com.pocket.repository;

import com.pocket.domain.Cotizacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Integer> {

    Optional<Cotizacion> findFirstByOrderByFechaActualizacionDesc();
}
