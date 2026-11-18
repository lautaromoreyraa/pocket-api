package com.pocket.dto.gastofijo;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record ResumenFijosResponse(
        YearMonth periodo,

        List<FijoDelPeriodoResponse> items,

        BigDecimal totalEstimado,

        BigDecimal totalRegistrado
) {}
