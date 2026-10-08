package com.pocket.dto.resumen;

import java.math.BigDecimal;

/**
 * RN-06 — capacidad de ahorro. Es **global**: resta débito más crédito, sin
 * importar la pestaña desde la que se pidió el resumen.
 *
 * RF-33: sin ingreso cargado no se muestra la capacidad de ahorro. Eso se
 * expresa devolviendo el balance entero en **null**, no con un flag al lado de
 * números igualmente poblados. La ausencia del objeto es más difícil de ignorar
 * por accidente que un booleano que hay que acordarse de mirar.
 */
public record BalanceResponse(
        BigDecimal ingresos,
        BigDecimal gastosTotales,
        BigDecimal capacidadAhorro
) {}
