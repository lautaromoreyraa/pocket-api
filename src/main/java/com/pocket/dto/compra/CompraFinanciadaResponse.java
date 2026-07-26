package com.pocket.dto.compra;

import com.pocket.dto.gasto.GastoResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CompraFinanciadaResponse(
        UUID id,
        BigDecimal montoTotal,
        Integer cantidadCuotas,
        String categoria,
        String descripcion,
        LocalDate fechaCompra,
        List<GastoResponse> cuotas
) {}
