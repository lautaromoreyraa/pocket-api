package com.pocket.mapper.gastofijo.impl;

import com.pocket.domain.GastoFijo;
import com.pocket.dto.gastofijo.GastoFijoResponse;
import com.pocket.mapper.gastofijo.GastoFijoMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GastoFijoMapperImpl implements GastoFijoMapper {

    @Override
    public GastoFijoResponse aResponse(GastoFijo f) {
        if (f == null) return null;

        return new GastoFijoResponse(
                f.getId(),
                f.getDescripcion(),
                f.getMonto(),
                f.getCategoria().getId(),
                f.getCategoria().getNombre(),
                f.getMedioPago(),
                f.getDiaDelMes(),
                f.isActivo()
        );
    }

    @Override
    public List<GastoFijoResponse> aResponse(List<GastoFijo> fijos) {
        return fijos == null ? List.of() : fijos.stream().map(this::aResponse).toList();
    }
}
