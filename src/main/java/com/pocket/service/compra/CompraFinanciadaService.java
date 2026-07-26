package com.pocket.service.compra;

import com.pocket.dto.compra.CompraFinanciadaRequest;
import com.pocket.dto.compra.CompraFinanciadaResponse;
import com.pocket.dto.resumen.CuotaEnCursoResponse;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface CompraFinanciadaService {

    CompraFinanciadaResponse registrar(CompraFinanciadaRequest request);

    void eliminar(UUID id);

    List<CuotaEnCursoResponse> cuotasEnCurso(YearMonth periodo);
}
