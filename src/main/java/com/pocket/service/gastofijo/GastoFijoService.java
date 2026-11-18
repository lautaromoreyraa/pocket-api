package com.pocket.service.gastofijo;

import com.pocket.dto.gasto.GastoResponse;
import com.pocket.dto.gastofijo.GastoFijoRequest;
import com.pocket.dto.gastofijo.GastoFijoResponse;
import com.pocket.dto.gastofijo.RegistroFijoRequest;
import com.pocket.dto.gastofijo.ResumenFijosResponse;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface GastoFijoService {

    List<GastoFijoResponse> listar();

    GastoFijoResponse registrar(GastoFijoRequest request);

    GastoFijoResponse editar(UUID id, GastoFijoRequest request);

    void eliminar(UUID id);

    ResumenFijosResponse resumenDelPeriodo(YearMonth periodo);

    GastoResponse registrarDelPeriodo(UUID id, RegistroFijoRequest request);

    GastoResponse editarMontoDelPeriodo(UUID id, YearMonth periodo, BigDecimal monto);
}
