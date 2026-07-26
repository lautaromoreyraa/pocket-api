package com.pocket.dto.resumen;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record ResumenResponse(
        YearMonth periodo,
        boolean credito,
        BigDecimal total,
        List<CategoriaResumenResponse> porCategoria,
        List<HormigaResponse> hormigas,
        List<CuotaEnCursoResponse> cuotasEnCurso,
        BalanceResponse balance
) {}
