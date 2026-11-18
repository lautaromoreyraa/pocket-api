package com.pocket.dto.resumen;

import com.pocket.dto.gasto.GastoResponse;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record ResumenResponse(
        YearMonth periodo,
        boolean credito,
        BigDecimal total,
        List<CategoriaResumenResponse> porCategoria,
        List<GastoResponse> ultimosMovimientos,

        List<CuotaEnCursoResponse> comprasEnCurso,

        BigDecimal comprometidoDelPeriodo,

        BalanceResponse balance,

        HormigaResponse avisoHormiga,

        BigDecimal promedioHistorico
) {}
