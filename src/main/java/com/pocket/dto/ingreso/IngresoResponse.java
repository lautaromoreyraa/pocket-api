package com.pocket.dto.ingreso;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record IngresoResponse(
        UUID id,
        BigDecimal monto,
        String descripcion,
        LocalDate periodo
) {}
