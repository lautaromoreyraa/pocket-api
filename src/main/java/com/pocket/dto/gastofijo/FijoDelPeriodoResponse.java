package com.pocket.dto.gastofijo;

import java.math.BigDecimal;
import java.util.UUID;

public record FijoDelPeriodoResponse(
        GastoFijoResponse fijo,

        UUID gastoId,

        BigDecimal montoRegistrado
) {}
