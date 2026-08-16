package com.pocket.dto.resumen;

import com.pocket.dto.gasto.GastoResponse;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * GET /api/resumen?periodo=&credito= — el único endpoint que alimenta Débito,
 * Crédito e Histórico (sección 4.3 del escenario).
 *
 * `ultimosMovimientos` es un preview corto, no el listado del período: la
 * pantalla "Todos los movimientos" es otra y se sirve de `GET /api/gastos`.
 */
public record ResumenResponse(
        YearMonth periodo,
        boolean credito,
        BigDecimal total,
        List<CategoriaResumenResponse> porCategoria,
        List<GastoResponse> ultimosMovimientos,

        /** Solo en la vista de crédito; vacío en débito. */
        List<CuotaEnCursoResponse> comprasEnCurso,

        /**
         * Suma de las cuotas que vencen en **el período que se está viendo**.
         * Solo en la vista de crédito; null en débito.
         *
         * El nombre importa: el frontend lo tenía como `comprometidoProximoPeriodo`
         * y eso describía otro mes. Es plata, así que el campo dice exactamente
         * qué suma.
         */
        BigDecimal comprometidoDelPeriodo,

        /** RF-33 — null si el período no tiene ingreso cargado. */
        BalanceResponse balance,

        /** RF-27 — null si ninguna categoría llegó al umbral. */
        HormigaResponse avisoHormiga,

        /** RF-29 — null hasta tener más de `pocket.promedio.meses-minimos` de historia. */
        BigDecimal promedioHistorico
) {}
