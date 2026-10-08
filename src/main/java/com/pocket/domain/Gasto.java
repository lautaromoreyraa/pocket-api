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

    /** No nulo solo si este gasto es una cuota de una compra financiada. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compra_financiada_id")
    private CompraFinanciada compraFinanciada;

    /**
     * No nulo solo si este gasto se materializó desde una plantilla de gasto fijo.
     *
     * Queda en null si después se borra la plantilla (ON DELETE SET NULL): los
     * meses ya pagados son historia y se conservan (RF-44). Por eso el `origen`
     * sigue diciendo FIJO aunque acá no haya nada.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gasto_fijo_id")
    private GastoFijo gastoFijo;

    /**
     * Clave generada por el cliente para evitar duplicados cuando se reintenta
     * el envío de un borrador offline (RF-41, RF-42).
     */
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

    /** Número de cuota dentro de la compra financiada. Nulo si no es cuota. */
    @Column(name = "nro_cuota")
    private Integer nroCuota;

    @CreatedDate
    @Column(name = "fecha_alta", nullable = false, updatable = false)
    private Instant fechaAlta;

    /** Las cuotas no computan como gasto hormiga (RN-02). */
    public boolean esCuota() {
        return compraFinanciada != null;
    }

    /**
     * Los gastos fijos tampoco computan como hormiga (RN-02).
     *
     * Se mira el `origen` y no la FK a propósito: si el usuario borra la
     * plantilla, `gastoFijo` queda en null pero el gasto sigue habiendo sido un
     * fijo, y la luz no pasa a ser un gasto hormiga retroactivamente.
     */
    public boolean esFijo() {
        return origen == OrigenGasto.FIJO;
    }
}
