package com.pocket.service.ingreso;

import com.pocket.domain.Ingreso;
import com.pocket.domain.Usuario;
import com.pocket.dto.ingreso.IngresoRequest;
import com.pocket.dto.ingreso.IngresoResponse;
import com.pocket.exception.IngresoNoEncontradoException;
import com.pocket.mapper.ingreso.impl.IngresoMapperImpl;
import com.pocket.repository.IngresoRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.ingreso.impl.IngresoServiceImpl;
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

class IngresoServiceImplTest {

    private IngresoRepository ingresoRepository;
    private AuthService authService;
    private IngresoServiceImpl service;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        ingresoRepository = mock(IngresoRepository.class);
        authService = mock(AuthService.class);
        service = new IngresoServiceImpl(ingresoRepository, new IngresoMapperImpl(), authService);

        usuario = Usuario.builder().id(UUID.randomUUID()).deviceUuid("dev").build();
        lenient().when(authService.actual()).thenReturn(usuario);
        lenient().when(ingresoRepository.save(any(Ingreso.class))).thenAnswer(inv -> {
            Ingreso i = inv.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            return i;
        });
    }

    private Ingreso ingresoDe(Usuario dueño, BigDecimal monto, LocalDate periodo) {
        return Ingreso.builder()
                .id(UUID.randomUUID()).usuario(dueño).monto(monto)
                .descripcion("sueldo").periodo(periodo).build();
    }

    @Test
    @DisplayName("Un ingreso con fecha 15/03 se guarda con período 01/03")
    void normalizaElPeriodoAlDiaUno() {
        IngresoResponse resp = service.registrar(new IngresoRequest(
                new BigDecimal("500000.00"), "sueldo", LocalDate.of(2026, 3, 15)));

        assertThat(guardado().getPeriodo()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(resp.periodo()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    @DisplayName("Varios ingresos en el mismo mes se listan todos")
    void listaTodosLosDelMes() {
        LocalDate marzo = LocalDate.of(2026, 3, 1);
        when(ingresoRepository.findByUsuarioIdAndPeriodo(usuario.getId(), marzo))
                .thenReturn(List.of(
                        ingresoDe(usuario, new BigDecimal("500000.00"), marzo),
                        ingresoDe(usuario, new BigDecimal("120000.00"), marzo)));

        List<IngresoResponse> lista = service.listarDelPeriodo(YearMonth.of(2026, 3));

        assertThat(lista).hasSize(2);
        verify(ingresoRepository).findByUsuarioIdAndPeriodo(usuario.getId(), marzo);
    }

    @Test
    @DisplayName("Eliminar un ingreso ajeno lo trata como inexistente (404)")
    void noSePuedeEliminarIngresoAjeno() {
        Usuario otro = Usuario.builder().id(UUID.randomUUID()).deviceUuid("otro").build();
        Ingreso ajeno = ingresoDe(otro, new BigDecimal("500000.00"), LocalDate.of(2026, 3, 1));
        when(ingresoRepository.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.eliminar(ajeno.getId()))
                .isInstanceOf(IngresoNoEncontradoException.class);
        verify(ingresoRepository, never()).delete(any());
    }

    private Ingreso guardado() {
        org.mockito.ArgumentCaptor<Ingreso> captor =
                org.mockito.ArgumentCaptor.forClass(Ingreso.class);
        verify(ingresoRepository).save(captor.capture());
        return captor.getValue();
    }
}
