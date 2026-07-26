package com.pocket.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categoria")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Categoria {

    /** Nombre de la categoría de fallback (RF-35). */
    public static final String OTROS = "Otros";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 60)
    private String nombre;

    @Column(nullable = false, length = 40)
    private String icono;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(nullable = false)
    private Integer orden;
}
