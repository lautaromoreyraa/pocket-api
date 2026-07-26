package com.pocket.mapper.gasto;

import com.pocket.domain.Gasto;
import com.pocket.dto.gasto.GastoResponse;

import java.util.List;

public interface GastoMapper {

    GastoResponse aResponse(Gasto gasto);

    List<GastoResponse> aResponse(List<Gasto> gastos);
}
