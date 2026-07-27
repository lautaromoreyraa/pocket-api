package com.pocket.service.compra;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.domain.CompraFinanciada;
import com.pocket.domain.Gasto;
import com.pocket.domain.Usuario;
import com.pocket.dto.compra.CompraEdicionRequest;
import com.pocket.dto.compra.CompraFinanciadaRequest;
import com.pocket.dto.compra.CompraFinanciadaResponse;
import com.pocket.exception.CompraNoEncontradaException;
import com.pocket.mapper.compra.impl.CompraFinanciadaMapperImpl;
import com.pocket.mapper.gasto.impl.GastoMapperImpl;
import com.pocket.repository.CategoriaRepository;
import com.pocket.repository.CompraFinanciadaRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.compra.impl.CompraFinanciadaServiceImpl;
import com.pocket.service.cuota.impl.CalculadoraCuotasServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompraFinanciadaServiceImplTest {

    private CompraFinanciadaRepository compraRepository;
    private CategoriaRepository categoriaRepository;
    private AuthService authService;
    private CompraFinanciadaServiceImpl service;

    private Usuario usuario;
    private Categoria categoria;

    @BeforeEach
    void setUp() {
        compraRepository = mock(CompraFinanciadaRepository.class);
        categoriaRepository = mock(CategoriaRepository.class);
        authService = mock(AuthService.class);

        PocketProperties props = new PocketProperties();
        CalculadoraCuotasServiceImpl calculadora = new CalculadoraCuotasServiceImpl(props);
        CompraFinanciadaMapperImpl mapper = new CompraFinanciadaMapperImpl(new GastoMapperImpl());

        service = new CompraFinanciadaServiceImpl(
                compraRepository, categoriaRepository, calculadora, mapper, authService);

        usuario = Usuario.builder().id(UUID.randomUUID()).deviceUuid("dev").build();
        categoria = categoria("Hogar", 1);
        lenient().when(authService.actual()).thenReturn(usuario);
        lenient().when(categoriaRepository.findById(1)).thenReturn(Optional.of(categoria));
        // save devuelve la misma compra con id, como haría la base.
        lenient().when(compraRepository.save(any(CompraFinanciada.class))).thenAnswer(inv -> {
            CompraFinanciada c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
    }

    private Categoria categoria(String nombre, int id) {
        return Categoria.builder().id(id).nombre(nombre).icono("i").color("#000").orden(id).build();
    }

    private CompraFinanciadaRequest request(UUID key, BigDecimal total, int cuotas, LocalDate fecha) {
        return new CompraFinanciadaRequest(key, total, cuotas, 1, "TV", fecha);
    }

    @Test
    @DisplayName("$12.500 en 9 cuotas genera 9 gastos que suman exactamente $12.500")
    void nueveCuotasSumanElTotalExacto() {
        UUID key = UUID.randomUUID();
        when(compraRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        CompraFinanciadaResponse resp = service.registrar(
                request(key, new BigDecimal("12500.00"), 9, LocalDate.of(2026, 1, 15)));

        CompraFinanciada guardada = guardada();
        assertThat(guardada.getCuotas()).hasSize(9);
        BigDecimal suma = guardada.getCuotas().stream()
                .map(Gasto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(suma).isEqualByComparingTo(new BigDecimal("12500.00"));
        assertThat(resp.cuotas()).hasSize(9);
    }

    @Test
    @DisplayName("Comprada el 15/01, la cuota 1 se imputa a febrero y la 9 a octubre")
    void imputacionDeLaPrimeraYUltimaCuota() {
        UUID key = UUID.randomUUID();
        when(compraRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        service.registrar(request(key, new BigDecimal("12500.00"), 9, LocalDate.of(2026, 1, 15)));

        List<Gasto> cuotas = guardada().getCuotas();
        Gasto primera = cuotas.stream().filter(g -> g.getNroCuota() == 1).findFirst().orElseThrow();
        Gasto ultima = cuotas.stream().filter(g -> g.getNroCuota() == 9).findFirst().orElseThrow();
        assertThat(primera.getFechaImputacion()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(ultima.getFechaImputacion()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("Registrar dos veces con la misma idempotencyKey no crea una segunda compra")
    void idempotenciaDevuelveLaExistente() {
        UUID key = UUID.randomUUID();
        CompraFinanciada existente = CompraFinanciada.builder()
                .id(UUID.randomUUID()).usuario(usuario).idempotencyKey(key).categoria(categoria)
                .descripcion("TV").montoTotal(new BigDecimal("12500.00")).cantidadCuotas(9)
                .fechaCompra(LocalDate.of(2026, 1, 15)).build();
        when(compraRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existente));

        CompraFinanciadaResponse resp = service.registrar(
                request(key, new BigDecimal("12500.00"), 9, LocalDate.of(2026, 1, 15)));

        assertThat(resp.id()).isEqualTo(existente.getId());
        verify(compraRepository, never()).save(any());
    }

    @Test
    @DisplayName("Editar la categoría cambia la de las 9 cuotas y deja los montos igual")
    void editarPropagaCategoriaSinTocarMontos() {
        CompraFinanciada compra = compraConCuotas();
        List<BigDecimal> montosOriginales = compra.getCuotas().stream().map(Gasto::getMonto).toList();
        when(compraRepository.findById(compra.getId())).thenReturn(Optional.of(compra));
        Categoria nueva = categoria("Entretenimiento", 8);
        when(categoriaRepository.findById(8)).thenReturn(Optional.of(nueva));

        service.editar(compra.getId(), new CompraEdicionRequest(8, "Smart TV"));

        assertThat(compra.getCategoria().getId()).isEqualTo(8);
        assertThat(compra.getCuotas()).allSatisfy(c -> {
            assertThat(c.getCategoria().getId()).isEqualTo(8);
            assertThat(c.getDescripcion()).isEqualTo("Smart TV");
        });
        List<BigDecimal> montosDespues = compra.getCuotas().stream().map(Gasto::getMonto).toList();
        assertThat(montosDespues).isEqualTo(montosOriginales);
    }

    @Test
    @DisplayName("Eliminar la compra la borra (y la cascada baja las 9 cuotas)")
    void eliminarBorraLaCompra() {
        CompraFinanciada compra = compraConCuotas();
        when(compraRepository.findById(compra.getId())).thenReturn(Optional.of(compra));

        service.eliminar(compra.getId());

        verify(compraRepository).delete(compra);
    }

    @Test
    @DisplayName("Una compra de otro usuario se trata como inexistente (404)")
    void compraAjenaDa404() {
        CompraFinanciada ajena = compraConCuotas();
        ajena.setUsuario(Usuario.builder().id(UUID.randomUUID()).deviceUuid("otro").build());
        when(compraRepository.findById(ajena.getId())).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> service.eliminar(ajena.getId()))
                .isInstanceOf(CompraNoEncontradaException.class);
        assertThatThrownBy(() -> service.editar(ajena.getId(), new CompraEdicionRequest(1, "x")))
                .isInstanceOf(CompraNoEncontradaException.class);
        verify(compraRepository, never()).delete(any());
    }

    // --- helpers -----------------------------------------------------------

    private CompraFinanciada guardada() {
        org.mockito.ArgumentCaptor<CompraFinanciada> captor =
                org.mockito.ArgumentCaptor.forClass(CompraFinanciada.class);
        verify(compraRepository).save(captor.capture());
        return captor.getValue();
    }

    /** Compra con sus 9 cuotas ya generadas por la calculadora real. */
    private CompraFinanciada compraConCuotas() {
        CompraFinanciada compra = CompraFinanciada.builder()
                .id(UUID.randomUUID()).usuario(usuario).idempotencyKey(UUID.randomUUID())
                .categoria(categoria).descripcion("TV").montoTotal(new BigDecimal("12500.00"))
                .cantidadCuotas(9).fechaCompra(LocalDate.of(2026, 1, 15)).build();
        compra.setCuotas(new CalculadoraCuotasServiceImpl(new PocketProperties()).generarCuotas(compra));
        return compra;
    }
}
