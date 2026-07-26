package com.pocket.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Entity
@Table(name = "compra_financiada")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class CompraFinanciada {

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

    private String descripcion;

    @Column(name = "monto_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoTotal;

    @Column(name = "cantidad_cuotas", nullable = false)
    private Integer cantidadCuotas;

    @Column(name = "fecha_compra", nullable = false)
    private LocalDate fechaCompra;

    @Builder.Default
    @OneToMany(mappedBy = "compraFinanciada", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Gasto> cuotas = new ArrayList<>();

    @CreatedDate
    @Column(name = "fecha_alta", nullable = false, updatable = false)
    private Instant fechaAlta;
}
