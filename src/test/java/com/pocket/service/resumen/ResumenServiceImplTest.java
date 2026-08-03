package com.pocket.service.resumen;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Usuario;
import com.pocket.dto.resumen.CategoriaResumenResponse;
import com.pocket.dto.resumen.CuotaEnCursoResponse;
import com.pocket.dto.resumen.HormigaResponse;
import com.pocket.dto.resumen.ResumenResponse;
import com.pocket.repository.GastoRepository;
import com.pocket.repository.IngresoRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.compra.CompraFinanciadaService;
import com.pocket.service.hormiga.HormigaService;
import com.pocket.service.resumen.impl.ResumenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
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

        Usuario usuario = Usuario.builder().id(usuarioId).deviceUuid("dev").build();
        lenient().when(authService.actual()).thenReturn(usuario);

        lenient().when(gastoRepository.agruparPorCategoria(eq(usuarioId), eq(desde), eq(hasta), anyBoolean(), anyBoolean()))
                .thenReturn(List.of());
        lenient().when(hormigaService.marcarHormigas(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(hormigaService.detectar(eq(usuarioId), eq(periodo), anyBoolean())).thenReturn(List.of());
        lenient().when(ingresoRepository.totalDelPeriodo(eq(usuarioId), eq(desde))).thenReturn(BigDecimal.ZERO);
        lenient().when(ingresoRepository.existsByUsuarioIdAndPeriodo(eq(usuarioId), eq(desde))).thenReturn(false);
        lenient().when(gastoRepository.totalDelPeriodo(eq(usuarioId), eq(desde), eq(hasta), any())).thenReturn(BigDecimal.ZERO);

        service = new ResumenServiceImpl(
                gastoRepository, ingresoRepository, hormigaService, compraService, authService, new PocketProperties());
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

    @Test
    @DisplayName("hormigas sale de hormigaService.detectar")
    void hormigasSaleDeHormigaService() {
        HormigaResponse hormiga = new HormigaResponse("Delivery", 3, new BigDecimal("15000.00"), null);
        when(hormigaService.detectar(usuarioId, periodo, false)).thenReturn(List.of(hormiga));

        ResumenResponse resp = service.armar(periodo, false);

        assertThat(resp.hormigas()).containsExactly(hormiga);
    }

    @Test
    @DisplayName("cuotasEnCurso queda vacío en la pestaña de débito")
    void cuotasEnCursoVacioEnDebito() {
        ResumenResponse resp = service.armar(periodo, false);

        assertThat(resp.cuotasEnCurso()).isEmpty();
        verify(compraService, never()).cuotasEnCurso(any());
    }

    @Test
    @DisplayName("cuotasEnCurso delega en CompraFinanciadaService en la pestaña de crédito")
    void cuotasEnCursoEnCredito() {
        CuotaEnCursoResponse cuota = new CuotaEnCursoResponse(
                UUID.randomUUID(), "TV", 2, 9, new BigDecimal("1388.00"), YearMonth.of(2026, 10));
        when(compraService.cuotasEnCurso(periodo)).thenReturn(List.of(cuota));

        ResumenResponse resp = service.armar(periodo, true);

        assertThat(resp.cuotasEnCurso()).containsExactly(cuota);
    }

    @Test
    @DisplayName("El ahorro global es el mismo número en la pestaña débito y en la de crédito")
    void ahorroEsGlobalIndependienteDeLaPestaña() {
        when(ingresoRepository.totalDelPeriodo(usuarioId, desde)).thenReturn(new BigDecimal("500000.00"));
        when(ingresoRepository.existsByUsuarioIdAndPeriodo(usuarioId, desde)).thenReturn(true);
        when(gastoRepository.totalDelPeriodo(usuarioId, desde, hasta, false)).thenReturn(new BigDecimal("100000.00"));
        when(gastoRepository.totalDelPeriodo(usuarioId, desde, hasta, true)).thenReturn(new BigDecimal("50000.00"));

        ResumenResponse debito = service.armar(periodo, false);
        ResumenResponse credito = service.armar(periodo, true);

        assertThat(debito.balance().capacidadAhorro()).isEqualByComparingTo("350000.00");
        assertThat(credito.balance().capacidadAhorro()).isEqualByComparingTo(debito.balance().capacidadAhorro());
    }

    @Test
    @DisplayName("Sin ingresos cargados, tieneIngresos es false pero capacidadAhorro sigue siendo un número, no null")
    void sinIngresosTieneIngresosEsFalseYAhorroNoEsNull() {
        when(gastoRepository.totalDelPeriodo(usuarioId, desde, hasta, false)).thenReturn(new BigDecimal("30000.00"));
        when(gastoRepository.totalDelPeriodo(usuarioId, desde, hasta, true)).thenReturn(BigDecimal.ZERO);

        ResumenResponse resp = service.armar(periodo, false);

        assertThat(resp.balance().tieneIngresos()).isFalse();
        assertThat(resp.balance().capacidadAhorro()).isNotNull();
        assertThat(resp.balance().capacidadAhorro()).isEqualByComparingTo("-30000.00");
    }

    @Test
    @DisplayName("Un ingreso cargado en $0 cuenta como 'tiene ingresos' (existe el registro, no importa la suma)")
    void ingresoEnCeroCuentaComoTieneIngresos() {
        when(ingresoRepository.totalDelPeriodo(usuarioId, desde)).thenReturn(BigDecimal.ZERO);
        when(ingresoRepository.existsByUsuarioIdAndPeriodo(usuarioId, desde)).thenReturn(true);

        ResumenResponse resp = service.armar(periodo, false);

        assertThat(resp.balance().tieneIngresos()).isTrue();
    }

    @Test
    @DisplayName("El promedio histórico todavía no está disponible")
    void promedioTodaviaNoDisponible() {
        ResumenResponse resp = service.armar(periodo, false);

        assertThat(resp.balance().promedioDisponible()).isFalse();
        assertThat(resp.balance().promedioHistorico()).isNull();
    }
}
