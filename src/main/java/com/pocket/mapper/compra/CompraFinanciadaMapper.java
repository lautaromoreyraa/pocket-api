package com.pocket.mapper.compra;

import com.pocket.domain.CompraFinanciada;
import com.pocket.dto.compra.CompraFinanciadaResponse;
import com.pocket.dto.resumen.CuotaEnCursoResponse;

import java.time.YearMonth;

public interface CompraFinanciadaMapper {

    CompraFinanciadaResponse aResponse(CompraFinanciada compra);

    CuotaEnCursoResponse aCuotaEnCurso(CompraFinanciada compra, YearMonth periodoActual);
}
