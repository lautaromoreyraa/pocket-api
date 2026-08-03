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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

        Map<Integer, BigDecimal> promediosPorCategoria =
                promediosPorCategoria(usuarioId, periodo, credito, incluirCuotas);

        return filas.stream()
                .filter(fila -> (long) fila[1] >= umbral())
                .map(fila -> {
                    Categoria categoria = (Categoria) fila[0];
                    long ocurrencias = (long) fila[1];
                    BigDecimal total = (BigDecimal) fila[2];
                    BigDecimal variacion = variacion(total, promediosPorCategoria.get(categoria.getId()));
                    return new HormigaResponse(categoria.getNombre(), ocurrencias, total, variacion);
                })
                .toList();
    }

    /** Promedio histórico por categoría en la ventana configurada (RN-05), con el
     *  mismo incluirCuotas que la detección actual para que la comparación sea
     *  contra lo mismo que se está midiendo. Vacío si no hay historia suficiente. */
    private Map<Integer, BigDecimal> promediosPorCategoria(UUID usuarioId, YearMonth periodo,
                                                            boolean credito, boolean incluirCuotas) {
        LocalDate primerFecha = gastoRepository.primerPeriodoConGastos(usuarioId);
        YearMonth primerPeriodo = primerFecha == null ? null : YearMonth.from(primerFecha);
        long historiaPrevia = primerPeriodo == null ? 0 : PeriodoUtil.mesesEntre(primerPeriodo, periodo);
        if (historiaPrevia <= props.getPromedio().getMesesMinimos()) {
            return Map.of();
        }

        List<YearMonth> ventana = PeriodoUtil.ventanaPromedio(
                periodo, primerPeriodo, props.getPromedio().getVentanaMeses());
        LocalDate ventanaDesde = PeriodoUtil.primerDia(ventana.get(0));
        LocalDate ventanaHasta = PeriodoUtil.ultimoDia(ventana.get(ventana.size() - 1));
        // Una sola query de rango: los meses sin gastos de la ventana suman $0
        // pero igual cuentan en el divisor (mismo criterio que el promedio global).
        List<Object[]> filasVentana = gastoRepository.agruparPorCategoria(
                usuarioId, ventanaDesde, ventanaHasta, credito, incluirCuotas);

        BigDecimal cantidadMeses = BigDecimal.valueOf(ventana.size());
        return filasVentana.stream().collect(Collectors.toMap(
                fila -> ((Categoria) fila[0]).getId(),
                fila -> ((BigDecimal) fila[2]).divide(cantidadMeses, 2, RoundingMode.HALF_UP)));
    }

    /** Variación porcentual del total actual contra el promedio de la categoría.
     *  Null sin promedio disponible, o si la categoría no tiene historia previa
     *  (promedio en $0: la variación porcentual contra cero no está definida). */
    private BigDecimal variacion(BigDecimal total, BigDecimal promedioCategoria) {
        if (promedioCategoria == null || promedioCategoria.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return total.subtract(promedioCategoria)
                .divide(promedioCategoria, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
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
