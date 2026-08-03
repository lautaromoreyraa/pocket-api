package com.pocket.service.hormiga.impl;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.dto.resumen.CategoriaResumenResponse;
import com.pocket.dto.resumen.HormigaResponse;
import com.pocket.repository.GastoRepository;
import com.pocket.service.hormiga.HormigaService;
import com.pocket.util.PeriodoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HormigaServiceImpl implements HormigaService {

    private final GastoRepository gastoRepository;
    private final PocketProperties props;

    @Override
    public List<HormigaResponse> detectar(UUID usuarioId, YearMonth periodo, boolean credito) {
        boolean incluirCuotas = !props.getHormiga().isExcluirCuotas();
        List<Object[]> filas = gastoRepository.agruparPorCategoria(
                usuarioId, PeriodoUtil.primerDia(periodo), PeriodoUtil.ultimoDia(periodo),
                credito, incluirCuotas);

        return filas.stream()
                .filter(fila -> (long) fila[1] >= umbral())
                .map(fila -> {
                    Categoria categoria = (Categoria) fila[0];
                    long ocurrencias = (long) fila[1];
                    BigDecimal total = (BigDecimal) fila[2];
                    // El promedio histórico todavía no está implementado.
                    return new HormigaResponse(categoria.getNombre(), ocurrencias, total, null);
                })
                .toList();
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
