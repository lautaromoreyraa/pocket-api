package com.pocket.domain;

import com.pocket.enumeration.MedioPago;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * La PLANTILLA de un gasto que se repite todos los meses: el alquiler, la luz,
 * una suscripción.
 *
 * No es un gasto y no suma a ningún total por sí misma. Cada mes se materializa
 * como una fila de {@link Gasto} con {@code origen = FIJO}, igual que una compra
 * financiada materializa sus cuotas (RF-22). La diferencia es el momento: las
 * cuotas se generan las N de una porque la compra tiene fin; un fijo no lo
 * tiene, así que se registra de a un mes, cuando el usuario confirma que lo pagó.
 */
@Entity
@Table(name = "gasto_fijo")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class GastoFijo {

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

    /** Lo que se espera pagar cada mes. El monto realmente pagado vive en el
     *  `gasto` que se genera, y puede diferir: la luz viene distinta cada vez. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago", nullable = false, length = 20)
    private MedioPago medioPago;

    /** Día del mes en que vence. Tope 28: con 29, 30 o 31 habría que decidir
     *  qué pasa en febrero y no vale la complejidad. */
    @Column(name = "dia_del_mes", nullable = false)
    private Integer diaDelMes;

    /** False = pausado: sigue existiendo pero no se espera este mes ni suma al
     *  estimado. Las suscripciones se dan de baja y se vuelven a contratar. */
    @Column(nullable = false)
    private boolean activo;

    @CreatedDate
    @Column(name = "fecha_alta", nullable = false, updatable = false)
    private Instant fechaAlta;

    /**
     * Cuándo vence dentro de un período dado.
     *
     * El día siempre existe porque está topeado en 28, así que no hay que
     * ajustarlo contra la longitud del mes.
     */
    public LocalDate vencimientoEn(YearMonth periodo) {
        return periodo.atDay(diaDelMes);
    }
}
