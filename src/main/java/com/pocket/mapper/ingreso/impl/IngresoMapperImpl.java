package com.pocket.mapper.ingreso.impl;

import com.pocket.domain.Ingreso;
import com.pocket.dto.ingreso.IngresoResponse;
import com.pocket.mapper.ingreso.IngresoMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IngresoMapperImpl implements IngresoMapper {

    @Override
    public IngresoResponse aResponse(Ingreso i) {
        if (i == null) return null;
        return new IngresoResponse(i.getId(), i.getMonto(), i.getDescripcion(), i.getPeriodo());
    }

    @Override
    public List<IngresoResponse> aResponse(List<Ingreso> ingresos) {
        return ingresos == null ? List.of() : ingresos.stream().map(this::aResponse).toList();
    }
}
