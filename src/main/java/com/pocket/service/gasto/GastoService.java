package com.pocket.service.gasto;

import com.pocket.dto.gasto.GastoRequest;
import com.pocket.dto.gasto.GastoResponse;
import com.pocket.enumeration.OrigenGasto;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Alta, edición, listado y baja de gastos (RF-11 a RF-14). */
public interface GastoService {

    /**
     * Registra un gasto. Idempotente: si ya existe uno con la misma
     * idempotencyKey, devuelve ese en lugar de crear un duplicado (RF-41).
     */
    GastoResponse registrar(GastoRequest request, OrigenGasto origen);

    /** Registra varios gastos detectados en un mismo audio (RF-05). */
    List<GastoResponse> registrarLote(List<GastoRequest> requests, OrigenGasto origen);

    GastoResponse editar(UUID id, GastoRequest request);

    void eliminar(UUID id);

    List<GastoResponse> listarDelPeriodo(YearMonth periodo, boolean credito);
}
