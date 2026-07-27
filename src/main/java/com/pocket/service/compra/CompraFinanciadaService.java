package com.pocket.service.compra;

import com.pocket.dto.compra.CompraEdicionRequest;
import com.pocket.dto.compra.CompraFinanciadaRequest;
import com.pocket.dto.compra.CompraFinanciadaResponse;
import com.pocket.dto.resumen.CuotaEnCursoResponse;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface CompraFinanciadaService {

    CompraFinanciadaResponse registrar(CompraFinanciadaRequest request);

    /** Cambia categoría y descripción, propagando a todas las cuotas.
     *  No toca montos, cantidad de cuotas ni fechas. */
    CompraFinanciadaResponse editar(UUID id, CompraEdicionRequest request);

    void eliminar(UUID id);

    List<CuotaEnCursoResponse> cuotasEnCurso(YearMonth periodo);
}
