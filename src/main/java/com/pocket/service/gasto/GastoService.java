package com.pocket.service.gasto;

import com.pocket.dto.gasto.GastoRequest;
import com.pocket.dto.gasto.GastoResponse;
import com.pocket.enumeration.OrigenGasto;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface GastoService {

    GastoResponse registrar(GastoRequest request, OrigenGasto origen);

    List<GastoResponse> registrarLote(List<GastoRequest> requests, OrigenGasto origen);

    GastoResponse editar(UUID id, GastoRequest request);

    void eliminar(UUID id);

    List<GastoResponse> listarDelPeriodo(YearMonth periodo, boolean credito);
}
