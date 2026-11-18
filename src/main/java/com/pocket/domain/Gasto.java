package com.pocket.domain;

import com.pocket.enumeration.MedioPago;
import com.pocket.enumeration.OrigenGasto;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "gasto")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Gasto {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compra_financiada_id")
    private CompraFinanciada compraFinanciada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gasto_fijo_id")
    private GastoFijo gastoFijo;

    @Column(name = "idempotency_key", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID idempotencyKey;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago", nullable = false, length = 20)
    private MedioPago medioPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrigenGasto origen;

    @Column(name = "fecha_gasto", nullable = false)
    private LocalDate fechaGasto;

    @Column(name = "fecha_imputacion", nullable = false)
    private LocalDate fechaImputacion;

    @Column(name = "nro_cuota")
    private Integer nroCuota;

    @CreatedDate
    @Column(name = "fecha_alta", nullable = false, updatable = false)
    private Instant fechaAlta;

    public boolean esCuota() {
        return compraFinanciada != null;
    }

    public boolean esFijo() {
        return origen == OrigenGasto.FIJO;
    }
}
