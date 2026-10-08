package com.pocket.mapper.gasto.impl;

import com.pocket.domain.Gasto;
import com.pocket.dto.gasto.GastoResponse;
import com.pocket.mapper.gasto.GastoMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GastoMapperImpl implements GastoMapper {

    @Override
    public GastoResponse aResponse(Gasto g) {
        return aResponse(g, null, 0);
    }

    @Override
    public List<GastoResponse> aResponse(List<Gasto> gastos) {
        return gastos == null ? List.of() : gastos.stream().map(this::aResponse).toList();
    }

    @Override
    public List<GastoResponse> aResponse(List<Gasto> gastos,
                                         Map<Integer, Long> ocurrenciasPorCategoria,
                                         int umbralHormiga) {
        if (gastos == null) return List.of();
        return gastos.stream()
                .map(g -> aResponse(g,
                        ocurrenciasPorCategoria == null
                                ? null : ocurrenciasPorCategoria.get(g.getCategoria().getId()),
                        umbralHormiga))
                .toList();
    }

    private GastoResponse aResponse(Gasto g, Long ocurrencias, int umbralHormiga) {
        if (g == null) return null;

        // Ni las cuotas ni los gastos fijos son hormiga (RN-02), aunque su
        // categoría se repita: son plata comprometida de antemano, no un gasto
        // chico y evitable que el usuario pueda decidir no repetir.
        boolean computaHormiga = !g.esCuota() && !g.esFijo();
        boolean hormiga = ocurrencias != null && computaHormiga && ocurrencias >= umbralHormiga;

        return new GastoResponse(
                g.getId(),
                g.getMonto(),
                g.getCategoria().getId(),
                g.getCategoria().getNombre(),
                g.getCategoria().getIcono(),
                g.getCategoria().getColor(),
                g.getDescripcion(),
                g.getMedioPago(),
                g.getOrigen(),
                g.getFechaGasto(),
                g.getFechaImputacion(),
                g.esCuota() ? g.getCompraFinanciada().getId() : null,
                g.getNroCuota(),
                g.esCuota() ? g.getCompraFinanciada().getCantidadCuotas() : null,
                hormiga,
                // El badge "7.ª VEZ" tampoco se muestra sobre una cuota o un
                // fijo: el contador cuenta repeticiones evitables de la
                // categoría, y colgárselo a la factura de luz diría que la
                // pagaste 7 veces, que no es lo que pasó.
                computaHormiga ? ocurrencias : null
        );
    }
}
