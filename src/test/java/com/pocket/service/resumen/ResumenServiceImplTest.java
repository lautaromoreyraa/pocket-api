package com.pocket.service.resumen;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.domain.CompraFinanciada;
import com.pocket.domain.Gasto;
import com.pocket.domain.Usuario;
import com.pocket.dto.gasto.GastoResponse;
import com.pocket.dto.resumen.CategoriaResumenResponse;
import com.pocket.dto.resumen.CuotaEnCursoResponse;
import com.pocket.dto.resumen.HormigaResponse;
import com.pocket.dto.resumen.ResumenResponse;
import com.pocket.enumeration.MedioPago;
import com.pocket.mapper.gasto.GastoMapper;
import com.pocket.mapper.gasto.impl.GastoMapperImpl;
import com.pocket.repository.GastoRepository;
import com.pocket.repository.IngresoRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.compra.CompraFinanciadaService;
import com.pocket.service.hormiga.HormigaService;
import com.pocket.service.resumen.impl.ResumenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumenServiceImplTest {

    private GastoRepository gastoRepository;
    private IngresoRepository ingresoRepository;
    private HormigaService hormigaService;
    private CompraFinanciadaService compraService;
    private AuthService authService;
    private ResumenServiceImpl service;

    private final UUID usuarioId = UUID.randomUUID();
    private final YearMonth periodo = YearMonth.of(2026, 3);
    private final LocalDate desde = LocalDate.of(2026, 3, 1);
    private final LocalDate hasta = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        gastoRepository = mock(GastoRepository.class);
        ingresoRepository = mock(IngresoRepository.class);
        hormigaService = mock(HormigaService.class);
        compraService = mock(CompraFinanciadaService.class);
        authService = mock(AuthService.class);
        // El mapper real, no un mock: así los tests de movimientos verifican de
        // verdad que el contador de repeticiones llega a cada fila.
        GastoMapper gastoMapper = new GastoMapperImpl();

        Usuario usuario = Usuario.builder().id(usuarioId).deviceUuid("dev").build();
        lenient().when(authService.actual()).thenReturn(usuario);

        lenient().when(gastoRepository.agruparPorCategoria(eq(usuarioId), eq(desde), eq(hasta), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of());
        lenient().when(gastoRepository.findUltimosDelPeriodo(eq(usuarioId), eq(desde), eq(hasta), anyBoolean(), any()))
                .thenReturn(List.of());
        lenient().when(gastoRepository.totalCuotasDelPeriodo(eq(usuarioId), eq(desde), eq(hasta)))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(hormigaService.marcarHormigas(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(hormigaService.detectar(eq(usuarioId), eq(periodo), anyBoolean())).thenReturn(List.of());
        lenient().when(hormigaService.umbral()).thenReturn(3);
        lenient().when(ingresoRepository.totalDelPeriodo(eq(usuarioId), eq(desde))).thenReturn(BigDecimal.ZERO);
        lenient().when(ingresoRepository.existsByUsuarioIdAndPeriodo(eq(usuarioId), eq(desde))).thenReturn(false);
        lenient().when(gastoRepository.totalDelPeriodo(eq(usuarioId), eq(desde), eq(hasta), any())).thenReturn(BigDecimal.ZERO);
        lenient().when(gastoRepository.totalDelPeriodo(eq(usuarioId), eq(desde), eq(hasta), isNull())).thenReturn(BigDecimal.ZERO);

        service = new ResumenServiceImpl(gastoRepository, ingresoRepository, hormigaService,
                compraService, authService, gastoMapper, new PocketProperties());
    }

    @Test
    @DisplayName("El total del período sale de totalDelPeriodo con el flag de la pestaña")
    void totalSegunLaPestaña() {
        when(gastoRepository.totalDelPeriodo(usuarioId, desde, hasta, true)).thenReturn(new BigDecimal("8000.00"));

        ResumenResponse resp = service.armar(periodo, true);

        assertThat(resp.total()).isEqualByComparingTo("8000.00");
        assertThat(resp.credito()).isTrue();
    }

    @Test
    @DisplayName("porCategoria pasa por hormigaService.marcarHormigas")
    void porCategoriaSeMarcaConHormigaService() {
        CategoriaResumenResponse marcada = new CategoriaResumenResponse(
                1, "Delivery", "icon", "#000", new BigDecimal("15000.00"), 3, true);
        when(hormigaService.marcarHormigas(any())).thenReturn(List.of(marcada));

        ResumenResponse resp = service.armar(periodo, false);

        assertThat(resp.porCategoria()).containsExactly(marcada);
    }

    // --- Aviso de hormiga -------------------------------------------------

    @Test
    @DisplayName("avisoHormiga es la categoría de mayor total, no la primera de la lista")
    void avisoHormigaEsLaDeMayorTotal() {
        HormigaResponse chica = new HormigaResponse("Delivery", 5, new BigDecimal("15000.00"), null);
        HormigaResponse grande = new HormigaResponse("Supermercado", 3, new BigDecimal("90000.00"), null);
        when(hormigaService.detectar(usuarioId, periodo, false)).thenReturn(List.of(chica, grande));

        ResumenResponse resp = service.armar(periodo, false);

        assertThat(resp.avisoHormiga()).isEqualTo(grande);
    }

    @Test
    @DisplayName("Sin hormigas, avisoHormiga viaja en null")
    void sinHormigasElAvisoEsNull() {
        assertThat(service.armar(periodo, false).avisoHormiga()).isNull();
    }

    // --- Movimientos ------------------------------------------------------

    @Test
    @DisplayName("El resumen trae los últimos movimientos del período")
    void traeLosUltimosMovimientos() {
        when(gastoRepository.findUltimosDelPeriodo(eq(usuarioId), eq(desde), eq(hasta), eq(false), any()))
                .thenReturn(List.of(gasto("Facturas", 1, new BigDecimal("5000.00"))));

        List<GastoResponse> movimientos = service.armar(periodo, false).ultimosMovimientos();

        assertThat(movimientos).hasSize(1);
        assertThat(movimientos.get(0).descripcion()).isEqualTo("Facturas");
        assertThat(movimientos.get(0).categoriaNombre()).isEqualTo("Delivery");
    }

    @Test
    @DisplayName("El tope de movimientos sale de configuración, no hardcodeado")
    void elTopeDeMovimientosSaleDeConfiguracion() {
        PocketProperties props = new PocketProperties();
        props.getResumen().setMovimientosMaximos(7);
        ResumenServiceImpl servicio = new ResumenServiceImpl(gastoRepository, ingresoRepository,
                hormigaService, compraService, authService, new GastoMapperImpl(), props);

        servicio.armar(periodo, false);

        verify(gastoRepository).findUltimosDelPeriodo(eq(usuarioId), eq(desde), eq(hasta), eq(false),
                eq(Pageable.ofSize(7)));
    }

    @Test
    @DisplayName("Cada movimiento trae cuántas veces se repitió su categoría en el período")
    void cadaMovimientoTraeSusOcurrencias() {
        Categoria delivery = categoria(1, "Delivery");
        totales(false, delivery, 7L, "58800.00");
        ocurrencias(false, delivery, 7L, "58800.00");
        when(gastoRepository.findUltimosDelPeriodo(eq(usuarioId), eq(desde), eq(hasta), eq(false), any()))
                .thenReturn(List.of(gasto("Empanadas", 1, new BigDecimal("8400.00"))));

        GastoResponse movimiento = service.armar(periodo, false).ultimosMovimientos().get(0);

        assertThat(movimiento.ocurrenciasCategoria()).isEqualTo(7L);
        assertThat(movimiento.hormiga()).isTrue();
    }

    @Test
    @DisplayName("Una categoría por debajo del umbral no marca el movimiento como hormiga")
    void pordebajoDelUmbralNoEsHormiga() {
        Categoria delivery = categoria(1, "Delivery");
        totales(false, delivery, 2L, "8000.00");
        ocurrencias(false, delivery, 2L, "8000.00");
        when(gastoRepository.findUltimosDelPeriodo(eq(usuarioId), eq(desde), eq(hasta), eq(false), any()))
                .thenReturn(List.of(gasto("Empanadas", 1, new BigDecimal("4000.00"))));

        GastoResponse movimiento = service.armar(periodo, false).ultimosMovimientos().get(0);

        assertThat(movimiento.ocurrenciasCategoria()).isEqualTo(2L);
        assertThat(movimiento.hormiga()).isFalse();
    }

    @Test
    @DisplayName("Una cuota nunca se marca como hormiga, aunque su categoría se repita (RN-02)")
    void laCuotaNoEsHormiga() {
        Categoria hogar = categoria(1, "Delivery");
        totales(true, hogar, 9L, "90000.00");
        ocurrencias(true, hogar, 9L, "90000.00");
        when(gastoRepository.findUltimosDelPeriodo(eq(usuarioId), eq(desde), eq(hasta), eq(true), any()))
                .thenReturn(List.of(cuota("TV", new BigDecimal("10000.00"), 2, 9)));

        GastoResponse movimiento = service.armar(periodo, true).ultimosMovimientos().get(0);

        assertThat(movimiento.hormiga()).isFalse();
        // El badge "N.ª VEZ" tampoco viaja: contarle 9 repeticiones a una cuota
        // diría que la pagaste 9 veces este mes.
        assertThat(movimiento.ocurrenciasCategoria()).isNull();
        assertThat(movimiento.nroCuota()).isEqualTo(2);
        assertThat(movimiento.compraFinanciadaId()).isNotNull();
    }

    @Test
    @DisplayName("El total de la categoría cuenta todo, pero las ocurrencias solo lo que puede ser hormiga")
    void totalYOcurrenciasSalenDeAgregadosDistintos() {
        Categoria servicios = categoria(1, "Servicios");
        // Tres filas de plata: dos gastos sueltos y un fijo…
        totales(false, servicios, 3L, "60000.00");
        // …pero solo dos cuentan como repeticiones evitables.
        ocurrencias(false, servicios, 2L, "15000.00");

        CategoriaResumenResponse fila = service.armar(periodo, false).porCategoria().get(0);

        assertThat(fila.total()).isEqualByComparingTo("60000.00");
        assertThat(fila.ocurrencias()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Una categoría con plata pero sin repeticiones contables muestra 0 ocurrencias, no se cae")
    void categoriaSoloConFijosMuestraCeroOcurrencias() {
        Categoria servicios = categoria(1, "Servicios");
        totales(false, servicios, 1L, "45000.00");
        // El agregado de ocurrencias no trae la categoría: el mes solo tuvo fijos.
        when(gastoRepository.agruparPorCategoria(usuarioId, desde, hasta, false, false, false))
                .thenReturn(List.of());

        CategoriaResumenResponse fila = service.armar(periodo, false).porCategoria().get(0);

        assertThat(fila.total()).isEqualByComparingTo("45000.00");
        assertThat(fila.ocurrencias()).isZero();
    }

    /** El agregado de PLATA: incluye cuotas y fijos. */
    private void totales(boolean credito, Categoria categoria, long cantidad, String total) {
        when(gastoRepository.agruparPorCategoria(usuarioId, desde, hasta, credito, true, true))
                .thenReturn(List.<Object[]>of(new Object[]{categoria, cantidad, new BigDecimal(total)}));
    }

    /** El agregado de REPETICIONES: sin cuotas ni fijos (RN-02). */
    private void ocurrencias(boolean credito, Categoria categoria, long cantidad, String total) {
        when(gastoRepository.agruparPorCategoria(usuarioId, desde, hasta, credito, false, false))
                .thenReturn(List.<Object[]>of(new Object[]{categoria, cantidad, new BigDecimal(total)}));
    }

    // --- Cuotas y comprometido -------------------------------------------

    @Test
    @DisplayName("comprasEnCurso queda vacío en la pestaña de débito")
    void comprasEnCursoVacioEnDebito() {
        ResumenResponse resp = service.armar(periodo, false);

        assertThat(resp.comprasEnCurso()).isEmpty();
        verify(compraService, never()).cuotasEnCurso(any());
    }

    @Test
    @DisplayName("comprasEnCurso delega en CompraFinanciadaService en la pestaña de crédito")
    void comprasEnCursoEnCredito() {
        CuotaEnCursoResponse compra = new CuotaEnCursoResponse(
                UUID.randomUUID(), "TV", "Hogar", new BigDecimal("125000.00"),
                new BigDecimal("1388.00"), 2, 9, YearMonth.of(2026, 10));
        when(compraService.cuotasEnCurso(periodo)).thenReturn(List.of(compra));

        assertThat(service.armar(periodo, true).comprasEnCurso()).containsExactly(compra);
    }

    @Test
    @DisplayName("El comprometido del período suma las cuotas que vencen en el mes que se está viendo")
    void comprometidoDelPeriodoSumaLasCuotasDelMes() {
        when(gastoRepository.totalCuotasDelPeriodo(usuarioId, desde, hasta))
                .thenReturn(new BigDecimal("63300.00"));

        assertThat(service.armar(periodo, true).comprometidoDelPeriodo())
                .isEqualByComparingTo("63300.00");
    }

    @Test
    @DisplayName("El comprometido es null en débito: es un concepto de la pestaña de crédito")
    void comprometidoEsNullEnDebito() {
        assertThat(service.armar(periodo, false).comprometidoDelPeriodo()).isNull();
        verify(gastoRepository, never()).totalCuotasDelPeriodo(any(), any(), any());
    }

    // --- Balance ----------------------------------------------------------

    @Test
    @DisplayName("El ahorro global es el mismo número en la pestaña débito y en la de crédito")
    void ahorroEsGlobalIndependienteDeLaPestaña() {
        when(ingresoRepository.totalDelPeriodo(usuarioId, desde)).thenReturn(new BigDecimal("500000.00"));
        when(ingresoRepository.existsByUsuarioIdAndPeriodo(usuarioId, desde)).thenReturn(true);
        when(gastoRepository.totalDelPeriodo(eq(usuarioId), eq(desde), eq(hasta), isNull()))
                .thenReturn(new BigDecimal("150000.00"));

        ResumenResponse debito = service.armar(periodo, false);
        ResumenResponse credito = service.armar(periodo, true);

        assertThat(debito.balance().capacidadAhorro()).isEqualByComparingTo("350000.00");
        assertThat(credito.balance().capacidadAhorro())
                .isEqualByComparingTo(debito.balance().capacidadAhorro());
    }

    @Test
    @DisplayName("Sin ingreso cargado el balance entero viaja en null (RF-33)")
    void sinIngresoElBalanceEsNull() {
        when(gastoRepository.totalDelPeriodo(eq(usuarioId), eq(desde), eq(hasta), isNull()))
                .thenReturn(new BigDecimal("30000.00"));

        assertThat(service.armar(periodo, false).balance()).isNull();
    }

    @Test
    @DisplayName("Un ingreso cargado en $0 sigue siendo un balance: cuenta el registro, no la suma")
    void ingresoEnCeroIgualDevuelveBalance() {
        when(ingresoRepository.totalDelPeriodo(usuarioId, desde)).thenReturn(BigDecimal.ZERO);
        when(ingresoRepository.existsByUsuarioIdAndPeriodo(usuarioId, desde)).thenReturn(true);

        assertThat(service.armar(periodo, false).balance()).isNotNull();
    }

    @Test
    @DisplayName("El balance resta débito más crédito, no solo el total de la pestaña")
    void elBalanceRestaLosDosMedios() {
        when(ingresoRepository.totalDelPeriodo(usuarioId, desde)).thenReturn(new BigDecimal("100000.00"));
        when(ingresoRepository.existsByUsuarioIdAndPeriodo(usuarioId, desde)).thenReturn(true);
        when(gastoRepository.totalDelPeriodo(eq(usuarioId), eq(desde), eq(hasta), isNull()))
                .thenReturn(new BigDecimal("40000.00"));

        assertThat(service.armar(periodo, false).balance().gastosTotales())
                .isEqualByComparingTo("40000.00");
    }

    // --- Promedio histórico ----------------------------------------------

    @Test
    @DisplayName("Sin historial de gastos, el promedio es null")
    void sinHistorialPromedioEsNull() {
        assertThat(service.armar(periodo, false).promedioHistorico()).isNull();
    }

    @Test
    @DisplayName("Con 2 meses previos el promedio sigue en null (RN-05: hace falta más de 2)")
    void promedioNoDisponibleConDosMesesPrevios() {
        when(gastoRepository.primerPeriodoConGastos(usuarioId)).thenReturn(LocalDate.of(2026, 1, 1));

        assertThat(service.armar(periodo, false).promedioHistorico()).isNull();
    }

    @Test
    @DisplayName("Con 3 meses previos el promedio ya sale")
    void promedioDisponibleConTresMesesPrevios() {
        when(gastoRepository.primerPeriodoConGastos(usuarioId)).thenReturn(LocalDate.of(2025, 12, 1));
        when(gastoRepository.totalDelPeriodo(
                usuarioId, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 28), false))
                .thenReturn(new BigDecimal("30000.00"));

        assertThat(service.armar(periodo, false).promedioHistorico()).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("El promedio se calcula sobre los meses anteriores, sin contar el actual")
    void promedioNoIncluyeElMesActual() {
        when(gastoRepository.primerPeriodoConGastos(usuarioId)).thenReturn(LocalDate.of(2025, 12, 1));
        when(gastoRepository.totalDelPeriodo(
                usuarioId, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 28), false))
                .thenReturn(new BigDecimal("30000.00"));
        when(gastoRepository.totalDelPeriodo(usuarioId, desde, hasta, false))
                .thenReturn(new BigDecimal("999999.00"));

        ResumenResponse resp = service.armar(periodo, false);

        assertThat(resp.total()).isEqualByComparingTo("999999.00");
        assertThat(resp.promedioHistorico()).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("El promedio es por pestaña: cada una contra el total de su propia pestaña")
    void promedioEsPorPestaña() {
        when(gastoRepository.primerPeriodoConGastos(usuarioId)).thenReturn(LocalDate.of(2025, 12, 1));
        when(gastoRepository.totalDelPeriodo(
                usuarioId, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 28), false))
                .thenReturn(new BigDecimal("30000.00"));
        when(gastoRepository.totalDelPeriodo(
                usuarioId, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 28), true))
                .thenReturn(new BigDecimal("9000.00"));

        assertThat(service.armar(periodo, false).promedioHistorico()).isEqualByComparingTo("10000.00");
        assertThat(service.armar(periodo, true).promedioHistorico()).isEqualByComparingTo("3000.00");
    }

    // --- Períodos con datos ----------------------------------------------

    @Test
    @DisplayName("periodosConDatos devuelve los meses únicos, del más viejo al más nuevo")
    void periodosConDatosDevuelveMesesUnicos() {
        when(gastoRepository.periodosConGastos(usuarioId)).thenReturn(List.of(
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 1, 15),
                LocalDate.of(2020, 3, 1)));

        assertThat(service.periodosConDatos())
                .containsExactly(YearMonth.of(2020, 1), YearMonth.of(2020, 3));
    }

    @Test
    @DisplayName("El Histórico solo lista meses cerrados: ni el actual ni los futuros")
    void periodosConDatosExcluyeElMesEnCursoYLosFuturos() {
        YearMonth enCurso = YearMonth.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
        when(gastoRepository.periodosConGastos(usuarioId)).thenReturn(List.of(
                enCurso.minusMonths(2).atDay(1),
                enCurso.minusMonths(1).atDay(1),
                enCurso.atDay(1),
                // Cuotas futuras materializadas (RF-22): tienen fila, pero no son historia.
                enCurso.plusMonths(1).atDay(1),
                enCurso.plusMonths(2).atDay(1)));

        assertThat(service.periodosConDatos())
                .containsExactly(enCurso.minusMonths(2), enCurso.minusMonths(1));
    }

    // --- Helpers ----------------------------------------------------------

    private Categoria categoria(Integer id, String nombre) {
        return Categoria.builder().id(id).nombre(nombre).icono("icon").color("#000").orden(id).build();
    }

    private Gasto gasto(String descripcion, Integer categoriaId, BigDecimal monto) {
        return Gasto.builder()
                .id(UUID.randomUUID())
                .categoria(categoria(categoriaId, "Delivery"))
                .monto(monto)
                .descripcion(descripcion)
                .medioPago(MedioPago.EFECTIVO)
                .fechaGasto(desde)
                .fechaImputacion(desde)
                .build();
    }

    private Gasto cuota(String descripcion, BigDecimal monto, int nroCuota, int cantidadCuotas) {
        CompraFinanciada compra = CompraFinanciada.builder()
                .id(UUID.randomUUID())
                .cantidadCuotas(cantidadCuotas)
                .build();
        Gasto g = gasto(descripcion, 1, monto);
        g.setMedioPago(MedioPago.CREDITO);
        g.setCompraFinanciada(compra);
        g.setNroCuota(nroCuota);
        return g;
    }
}
