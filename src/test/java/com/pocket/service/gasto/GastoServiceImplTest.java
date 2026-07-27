package com.pocket.service.gasto;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.domain.CompraFinanciada;
import com.pocket.domain.Gasto;
import com.pocket.domain.Usuario;
import com.pocket.dto.gasto.GastoRequest;
import com.pocket.dto.gasto.GastoResponse;
import com.pocket.enumeration.MedioPago;
import com.pocket.enumeration.OrigenGasto;
import com.pocket.exception.CategoriaNoEncontradaException;
import com.pocket.exception.GastoNoEncontradoException;
import com.pocket.exception.OperacionNoPermitidaException;
import com.pocket.mapper.gasto.impl.GastoMapperImpl;
import com.pocket.repository.CategoriaRepository;
import com.pocket.repository.GastoRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.gasto.impl.GastoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GastoServiceImplTest {

    @Mock private GastoRepository gastoRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Spy private GastoMapperImpl gastoMapper;
    @Mock private AuthService authService;
    @Spy private PocketProperties props;

    @InjectMocks private GastoServiceImpl service;

    private Usuario usuario;
    private Categoria categoria;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder().id(UUID.randomUUID()).deviceUuid("dev").build();
        categoria = Categoria.builder()
                .id(1).nombre("Supermercado").icono("cart").color("#000").orden(1).build();
        lenient().when(authService.actual()).thenReturn(usuario);
        lenient().when(categoriaRepository.findById(1)).thenReturn(Optional.of(categoria));
        // save devuelve el mismo gasto con un id asignado, como haría la base.
        lenient().when(gastoRepository.save(any(Gasto.class))).thenAnswer(inv -> {
            Gasto g = inv.getArgument(0);
            if (g.getId() == null) g.setId(UUID.randomUUID());
            return g;
        });
    }

    private GastoRequest request(UUID key, MedioPago medio, LocalDate fecha) {
        return new GastoRequest(key, new BigDecimal("1500.00"), 1, "compra", medio, fecha);
    }

    @Test
    @DisplayName("Registrar dos veces con la misma idempotencyKey no crea un segundo gasto")
    void idempotenciaDevuelveElExistente() {
        UUID key = UUID.randomUUID();
        UUID idExistente = UUID.randomUUID();
        Gasto existente = Gasto.builder()
                .id(idExistente).usuario(usuario).categoria(categoria).idempotencyKey(key)
                .monto(new BigDecimal("1500.00")).medioPago(MedioPago.EFECTIVO)
                .origen(OrigenGasto.MANUAL)
                .fechaGasto(LocalDate.of(2026, 1, 20)).fechaImputacion(LocalDate.of(2026, 1, 1))
                .build();
        when(gastoRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existente));

        GastoResponse resp = service.registrar(
                request(key, MedioPago.EFECTIVO, LocalDate.of(2026, 1, 20)), OrigenGasto.MANUAL);

        assertThat(resp.id()).isEqualTo(idExistente);
        verify(gastoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un gasto con EFECTIVO el 20/01 se imputa a enero")
    void efectivoSeImputaAlMismoMes() {
        UUID key = UUID.randomUUID();
        when(gastoRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        service.registrar(request(key, MedioPago.EFECTIVO, LocalDate.of(2026, 1, 20)),
                OrigenGasto.MANUAL);

        assertThat(guardado().getFechaImputacion()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("Un gasto con CREDITO el 20/01 se imputa a febrero")
    void creditoSeImputaAlMesSiguiente() {
        UUID key = UUID.randomUUID();
        when(gastoRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        service.registrar(request(key, MedioPago.CREDITO, LocalDate.of(2026, 1, 20)),
                OrigenGasto.MANUAL);

        assertThat(guardado().getFechaImputacion()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    @DisplayName("Registrar con una categoría inexistente lanza 404 de categoría")
    void categoriaInexistenteFalla() {
        UUID key = UUID.randomUUID();
        when(gastoRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(categoriaRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(
                request(key, MedioPago.EFECTIVO, LocalDate.of(2026, 1, 20)), OrigenGasto.MANUAL))
                .isInstanceOf(CategoriaNoEncontradaException.class);
    }

    @Test
    @DisplayName("Editar un gasto ajeno lo trata como inexistente (404)")
    void noSePuedeEditarGastoAjeno() {
        Gasto ajeno = gastoPropio(otroUsuario(), MedioPago.EFECTIVO);
        when(gastoRepository.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.editar(ajeno.getId(),
                request(UUID.randomUUID(), MedioPago.EFECTIVO, LocalDate.of(2026, 1, 20))))
                .isInstanceOf(GastoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Eliminar un gasto ajeno lo trata como inexistente (404)")
    void noSePuedeEliminarGastoAjeno() {
        Gasto ajeno = gastoPropio(otroUsuario(), MedioPago.EFECTIVO);
        when(gastoRepository.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.eliminar(ajeno.getId()))
                .isInstanceOf(GastoNoEncontradoException.class);
        verify(gastoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Editar de DEBITO a CREDITO recalcula la imputación y no toca la idempotencyKey")
    void editarDebitoACreditoMueveImputacion() {
        Gasto propio = gastoPropio(usuario, MedioPago.DEBITO);
        UUID keyOriginal = propio.getIdempotencyKey();
        when(gastoRepository.findById(propio.getId())).thenReturn(Optional.of(propio));

        service.editar(propio.getId(),
                request(UUID.randomUUID(), MedioPago.CREDITO, LocalDate.of(2026, 1, 20)));

        assertThat(propio.getMedioPago()).isEqualTo(MedioPago.CREDITO);
        assertThat(propio.getFechaImputacion()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(propio.getIdempotencyKey()).isEqualTo(keyOriginal);
    }

    @Test
    @DisplayName("No se puede editar una cuota individual: 409")
    void noSePuedeEditarUnaCuota() {
        Gasto cuota = gastoPropio(usuario, MedioPago.CREDITO);
        cuota.setCompraFinanciada(CompraFinanciada.builder().id(UUID.randomUUID()).build());
        when(gastoRepository.findById(cuota.getId())).thenReturn(Optional.of(cuota));

        assertThatThrownBy(() -> service.editar(cuota.getId(),
                request(UUID.randomUUID(), MedioPago.CREDITO, LocalDate.of(2026, 1, 20))))
                .isInstanceOf(OperacionNoPermitidaException.class);
        verify(gastoRepository, never()).save(any());
    }

    @Test
    @DisplayName("No se puede eliminar una cuota individual: 409 con mensaje claro")
    void noSePuedeEliminarUnaCuota() {
        Gasto cuota = gastoPropio(usuario, MedioPago.CREDITO);
        cuota.setCompraFinanciada(CompraFinanciada.builder().id(UUID.randomUUID()).build());
        when(gastoRepository.findById(cuota.getId())).thenReturn(Optional.of(cuota));

        assertThatThrownBy(() -> service.eliminar(cuota.getId()))
                .isInstanceOf(OperacionNoPermitidaException.class)
                .hasMessageContaining("compra financiada");
        verify(gastoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Listar del período delega en findDelPeriodo con el rango del mes")
    void listarUsaElRangoDelMes() {
        when(gastoRepository.findDelPeriodo(any(), any(), any(), any(Boolean.class)))
                .thenReturn(java.util.List.of());

        service.listarDelPeriodo(YearMonth.of(2026, 1), false);

        verify(gastoRepository).findDelPeriodo(
                usuario.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), false);
    }

    // --- helpers -----------------------------------------------------------

    private Gasto guardado() {
        ArgumentCaptor<Gasto> captor = ArgumentCaptor.forClass(Gasto.class);
        verify(gastoRepository).save(captor.capture());
        return captor.getValue();
    }

    private Usuario otroUsuario() {
        return Usuario.builder().id(UUID.randomUUID()).deviceUuid("otro").build();
    }

    private Gasto gastoPropio(Usuario dueño, MedioPago medio) {
        return Gasto.builder()
                .id(UUID.randomUUID()).usuario(dueño).categoria(categoria)
                .idempotencyKey(UUID.randomUUID()).monto(new BigDecimal("1500.00"))
                .medioPago(medio).origen(OrigenGasto.MANUAL)
                .fechaGasto(LocalDate.of(2026, 1, 20)).fechaImputacion(LocalDate.of(2026, 1, 1))
                .build();
    }
}
