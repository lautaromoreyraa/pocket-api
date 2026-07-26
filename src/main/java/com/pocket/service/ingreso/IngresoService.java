package com.pocket.service.ingreso;

import com.pocket.dto.ingreso.IngresoRequest;
import com.pocket.dto.ingreso.IngresoResponse;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface IngresoService {

    IngresoResponse registrar(IngresoRequest request);

    List<IngresoResponse> listarDelPeriodo(YearMonth periodo);

    void eliminar(UUID id);
}
