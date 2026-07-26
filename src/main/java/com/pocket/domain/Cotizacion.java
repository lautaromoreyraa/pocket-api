package com.pocket.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cotizacion")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Cotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "valor_compra", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorCompra;

    @Column(name = "valor_venta", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorVenta;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;
}
