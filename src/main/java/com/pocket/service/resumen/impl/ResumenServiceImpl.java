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

        // Dos agregados, no uno, porque son dos preguntas distintas.
        //
        // El gráfico muestra PLATA y tiene que mostrarla toda: una cuota y un
        // fijo son plata que salió del bolsillo.
        List<Object[]> agrupado =
                gastoRepository.agruparPorCategoria(usuarioId, desde, hasta, credito, true, true);

        // El contador de REPETICIONES manda a la detección de hormigas, y ahí
        // rige RN-02: ni cuotas ni fijos. Antes se reusaba el agregado de arriba
        // y eso hacía que una categoría con 3 cuotas pintara la barra en rojo
        // pero no generara aviso: el mismo período se contestaba distinto según
        // qué parte de la pantalla preguntara.
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

    /**
     * RF-45 — los meses del selector del Histórico: solo los **cerrados**.
     *
     * El mes en curso queda afuera porque todavía no terminó, y los futuros
     * también aunque tengan filas: las cuotas se materializan con
     * `fecha_imputacion` futura (RF-22), así que una compra en 12 cuotas hecha
     * hoy dejaría un año de meses "con datos" en una pantalla que se llama
     * "Mirá para atrás".
     */
    @Override
    public List<YearMonth> periodosConDatos() {
        YearMonth mesEnCurso = YearMonth.now(ZoneId.of(props.getPeriodo().getZonaHoraria()));

        return gastoRepository.periodosConGastos(authService.actual().getId()).stream()
                .map(YearMonth::from)
                .filter(mes -> mes.isBefore(mesEnCurso))
                .distinct()
                .toList();
    }

    /**
     * RF-33 — sin ingreso cargado no hay capacidad de ahorro, y eso se expresa
     * devolviendo null, no un cero que se confunde con "gastaste todo".
     *
     * La capacidad de ahorro es **global** (RN-06): resta débito más crédito
     * siempre, sin importar desde qué pestaña se pidió el resumen.
     */
    private BalanceResponse armarBalance(UUID usuarioId, LocalDate desde, LocalDate hasta) {
        // Un ingreso de $0 sigue siendo "cargó ingresos": el criterio es la
        // existencia del registro, no que la suma dé positivo.
        if (!ingresoRepository.existsByUsuarioIdAndPeriodo(usuarioId, desde)) {
            return null;
        }

        BigDecimal ingresos = ingresoRepository.totalDelPeriodo(usuarioId, desde);
        BigDecimal gastosTotales = gastoRepository.totalDelPeriodo(usuarioId, desde, hasta, null);

        return new BalanceResponse(ingresos, gastosTotales, ingresos.subtract(gastosTotales));
    }

    /**
     * RF-27 — un solo aviso, el de mayor total. El desglose completo ya viaja
     * en `porCategoria`, donde cada fila trae su propio flag.
     */
    private HormigaResponse avisoHormiga(UUID usuarioId, YearMonth periodo, boolean credito) {
        return hormigaService.detectar(usuarioId, periodo, credito).stream()
                .max(Comparator.comparing(HormigaResponse::total))
                .orElse(null);
    }

    /**
     * RF-29, RN-05 — null hasta tener más de `meses-minimos` de historia.
     *
     * A diferencia de la capacidad de ahorro, el promedio es **por pestaña**: se
     * compara contra el total de la misma vista (RF-30), no contra el global.
     */
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
        // Una sola query de rango en vez de una por mes. Los meses sin gastos
        // dentro de la ventana suman $0 pero igual cuentan en el divisor: si no,
        // el promedio de alguien que gastó un solo mes daría ese mes entero.
        BigDecimal suma = gastoRepository.totalDelPeriodo(usuarioId,
                PeriodoUtil.primerDia(ventana.get(0)),
                PeriodoUtil.ultimoDia(ventana.get(ventana.size() - 1)),
                credito);

        return suma.divide(BigDecimal.valueOf(ventana.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Cuántas veces se repitió cada categoría, contando solo lo que puede ser
     * hormiga: sin cuotas y sin gastos fijos (RN-02).
     *
     * Es el número que alimenta tanto las barras rojas como el badge "7.ª VEZ"
     * de cada movimiento, así que los dos dicen siempre lo mismo.
     */
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

    /**
     * El total sale del agregado completo y las ocurrencias del filtrado, así
     * que una categoría puede mostrar plata con cero repeticiones: es un mes en
     * el que solo hubo cuotas o fijos. Es la lectura correcta —salió plata, no
     * hubo nada que evitar— y no un cero que falta.
     */
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
