package com.pocket.mapper.ingreso;

import com.pocket.domain.Ingreso;
import com.pocket.dto.ingreso.IngresoResponse;

import java.util.List;

public interface IngresoMapper {
    IngresoResponse aResponse(Ingreso ingreso);
    List<IngresoResponse> aResponse(List<Ingreso> ingresos);
}
