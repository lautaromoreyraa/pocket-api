package com.pocket.service.resumen.impl;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.domain.Gasto;
import com.pocket.domain.Usuario;
import com.pocket.dto.gasto.GastoResponse;
import com.pocket.dto.resumen.BalanceResponse;
import com.pocket.dto.resumen.CategoriaResumenResponse;
import com.pocket.dto.resumen.CuotaEnCursoResponse;
import com.pocket.dto.resumen.HormigaResponse;
import com.pocket.dto.resumen.ResumenResponse;
import com.pocket.mapper.gasto.GastoMapper;
import com.pocket.repository.GastoRepository;
import com.pocket.repository.IngresoRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.compra.CompraFinanciadaService;
import com.pocket.service.hormiga.HormigaService;
import com.pocket.service.resumen.ResumenService;
import com.pocket.util.PeriodoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResumenServiceImpl implements ResumenService {

    private final GastoRepository gastoRepository;
    private final IngresoRepository ingresoRepository;
    private final HormigaService hormigaService;
    private final CompraFinanciadaService compraService;
    private final AuthService authService;
    private final GastoMapper gastoMapper;
    private final PocketProperties props;

    @Override
    public ResumenResponse armar(YearMonth periodo, boolean credito) {
        Usuario usuario = authService.actual();
        UUID usuarioId = usuario.getId();
        LocalDate desde = PeriodoUtil.primerDia(periodo);
        LocalDate hasta = PeriodoUtil.ultimoDia(periodo);

        BigDecimal total = gastoRepository.totalDelPeriodo(usuarioId, desde, hasta, credito);

        List<Object[]> agrupado =
                gastoRepository.agruparPorCategoria(usuarioId, desde, hasta, credito, true, true);

        Map<Integer, Long> ocurrenciasPorCategoria = ocurrenciasHormiga(usuarioId, desde, hasta, credito);

        List<CategoriaResumenResponse> porCategoria = hormigaService.marcarHormigas(
                agrupado.stream()
                        .map(fila -> aCategoriaResumen(fila, ocurrenciasPorCategoria))
                        .toList());

        List<Gasto> ultimos = gastoRepository.findUltimosDelPeriodo(
                usuarioId, desde, hasta, credito,
                PageRequest.of(0, props.getResumen().getMovimientosMaximos()));
        List<GastoResponse> ultimosMovimientos = gastoMapper.aResponse(
                ultimos, ocurrenciasPorCategoria, hormigaService.umbral());

        List<CuotaEnCursoResponse> comprasEnCurso =
                credito ? compraService.cuotasEnCurso(periodo) : List.of();
        BigDecimal comprometidoDelPeriodo =
                credito ? gastoRepository.totalCuotasDelPeriodo(usuarioId, desde, hasta) : null;

        return new ResumenResponse(
                periodo, credito, total,
                porCategoria,
                ultimosMovimientos,
                comprasEnCurso,
                comprometidoDelPeriodo,
                armarBalance(usuarioId, desde, hasta),
                avisoHormiga(usuarioId, periodo, credito),
                promedioHistorico(usuarioId, periodo, credito));
    }

    @Override
    public List<YearMonth> periodosConDatos() {
        YearMonth mesEnCurso = YearMonth.now(ZoneId.of(props.getPeriodo().getZonaHoraria()));

        return gastoRepository.periodosConGastos(authService.actual().getId()).stream()
                .map(YearMonth::from)
                .filter(mes -> mes.isBefore(mesEnCurso))
                .distinct()
                .toList();
    }

    private BalanceResponse armarBalance(UUID usuarioId, LocalDate desde, LocalDate hasta) {
        if (!ingresoRepository.existsByUsuarioIdAndPeriodo(usuarioId, desde)) {
            return null;
        }

        BigDecimal ingresos = ingresoRepository.totalDelPeriodo(usuarioId, desde);
        BigDecimal gastosTotales = gastoRepository.totalDelPeriodo(usuarioId, desde, hasta, null);

        return new BalanceResponse(ingresos, gastosTotales, ingresos.subtract(gastosTotales));
    }

    private HormigaResponse avisoHormiga(UUID usuarioId, YearMonth periodo, boolean credito) {
        return hormigaService.detectar(usuarioId, periodo, credito).stream()
                .max(Comparator.comparing(HormigaResponse::total))
                .orElse(null);
    }

    private BigDecimal promedioHistorico(UUID usuarioId, YearMonth periodo, boolean credito) {
        LocalDate primerFecha = gastoRepository.primerPeriodoConGastos(usuarioId);
        if (primerFecha == null) {
            return null;
        }

        YearMonth primerPeriodo = YearMonth.from(primerFecha);
        long historiaPrevia = PeriodoUtil.mesesEntre(primerPeriodo, periodo);
        if (historiaPrevia <= props.getPromedio().getMesesMinimos()) {
            return null;
        }

        List<YearMonth> ventana = PeriodoUtil.ventanaPromedio(
                periodo, primerPeriodo, props.getPromedio().getVentanaMeses());
        BigDecimal suma = gastoRepository.totalDelPeriodo(usuarioId,
                PeriodoUtil.primerDia(ventana.get(0)),
                PeriodoUtil.ultimoDia(ventana.get(ventana.size() - 1)),
                credito);

        return suma.divide(BigDecimal.valueOf(ventana.size()), 2, RoundingMode.HALF_UP);
    }

    private Map<Integer, Long> ocurrenciasHormiga(UUID usuarioId, LocalDate desde,
                                                  LocalDate hasta, boolean credito) {
        List<Object[]> filas = gastoRepository.agruparPorCategoria(
                usuarioId, desde, hasta, credito,
                !props.getHormiga().isExcluirCuotas(),
                !props.getHormiga().isExcluirFijos());

        return filas.stream().collect(Collectors.toMap(
                fila -> ((Categoria) fila[0]).getId(),
                fila -> (long) fila[1]));
    }

    private CategoriaResumenResponse aCategoriaResumen(Object[] fila,
                                                       Map<Integer, Long> ocurrenciasPorCategoria) {
        Categoria categoria = (Categoria) fila[0];
        BigDecimal total = (BigDecimal) fila[2];
        long ocurrencias = ocurrenciasPorCategoria.getOrDefault(categoria.getId(), 0L);
        return new CategoriaResumenResponse(
                categoria.getId(), categoria.getNombre(), categoria.getIcono(), categoria.getColor(),
                total, ocurrencias, false);
    }
}
