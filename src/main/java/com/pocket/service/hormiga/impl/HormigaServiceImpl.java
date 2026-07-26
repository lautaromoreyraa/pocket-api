package com.pocket.service.hormiga.impl;

import com.pocket.config.PocketProperties;
import com.pocket.dto.resumen.CategoriaResumenResponse;
import com.pocket.dto.resumen.HormigaResponse;
import com.pocket.service.hormiga.HormigaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HormigaServiceImpl implements HormigaService {

    private final PocketProperties props;

    @Override
    public List<HormigaResponse> detectar(UUID usuarioId, YearMonth periodo, boolean credito) {
        // TODO: consultar GastoRepository.agruparPorCategoria con
        //       incluirCuotas = !props.getHormiga().isExcluirCuotas()
        //       y filtrar las que superen el umbral.
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public List<CategoriaResumenResponse> marcarHormigas(List<CategoriaResumenResponse> categorias) {
        return categorias.stream()
                .map(c -> new CategoriaResumenResponse(
                        c.categoriaId(), c.nombre(), c.icono(), c.color(),
                        c.total(), c.ocurrencias(), c.ocurrencias() >= umbral()))
                .toList();
    }

    @Override
    public int umbral() {
        return props.getHormiga().getUmbralOcurrencias();
    }
}
