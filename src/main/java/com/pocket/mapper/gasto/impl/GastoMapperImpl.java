package com.pocket.mapper.gasto.impl;

import com.pocket.domain.Gasto;
import com.pocket.dto.gasto.GastoResponse;
import com.pocket.mapper.gasto.GastoMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GastoMapperImpl implements GastoMapper {

    @Override
    public GastoResponse aResponse(Gasto g) {
        if (g == null) return null;

        return new GastoResponse(
                g.getId(),
                g.getMonto(),
                g.getCategoria().getNombre(),
                g.getCategoria().getIcono(),
                g.getCategoria().getColor(),
                g.getDescripcion(),
                g.getMedioPago(),
                g.getOrigen(),
                g.getFechaGasto(),
                g.getFechaImputacion(),
                g.getNroCuota(),
                g.esCuota() ? g.getCompraFinanciada().getCantidadCuotas() : null,
                g.esCuota()
        );
    }

    @Override
    public List<GastoResponse> aResponse(List<Gasto> gastos) {
        return gastos == null ? List.of() : gastos.stream().map(this::aResponse).toList();
    }
}
