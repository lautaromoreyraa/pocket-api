package com.pocket.mapper.gastofijo;

import com.pocket.domain.GastoFijo;
import com.pocket.dto.gastofijo.GastoFijoResponse;

import java.util.List;

public interface GastoFijoMapper {

    GastoFijoResponse aResponse(GastoFijo fijo);

    List<GastoFijoResponse> aResponse(List<GastoFijo> fijos);
}
