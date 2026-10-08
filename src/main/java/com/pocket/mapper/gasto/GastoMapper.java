package com.pocket.mapper.gasto;

import com.pocket.domain.Gasto;
import com.pocket.dto.gasto.GastoResponse;

import java.util.List;
import java.util.Map;

public interface GastoMapper {

    /** Sin contexto de período: `hormiga` en false y `ocurrenciasCategoria` en null. */
    GastoResponse aResponse(Gasto gasto);

    List<GastoResponse> aResponse(List<Gasto> gastos);

    /**
     * Con el contador de repeticiones del período, para el "7.ª VEZ" de cada
     * movimiento. El mapa va de id de categoría a ocurrencias; las categorías
     * que no estén quedan sin contar.
     */
    List<GastoResponse> aResponse(List<Gasto> gastos, Map<Integer, Long> ocurrenciasPorCategoria,
                                  int umbralHormiga);
}
