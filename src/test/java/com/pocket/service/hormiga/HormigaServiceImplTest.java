package com.pocket.service.hormiga;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.dto.resumen.CategoriaResumenResponse;
import com.pocket.dto.resumen.HormigaResponse;
import com.pocket.repository.GastoRepository;
import com.pocket.service.hormiga.impl.HormigaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HormigaServiceImplTest {

    private GastoRepository gastoRepository;
    private HormigaServiceImpl service;

    private final UUID usuarioId = UUID.randomUUID();
    private final YearMonth periodo = YearMonth.of(2026, 3);
    private final LocalDate desde = LocalDate.of(2026, 3, 1);
    private final LocalDate hasta = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        gastoRepository = mock(GastoRepository.class);
        service = new HormigaServiceImpl(gastoRepository, new PocketProperties());
    }

    private List<Object[]> fila(String nombre, long ocurrencias, String total) {
        Categoria categoria = Categoria.builder().id(1).nombre(nombre).icono("i").color("#000").orden(1).build();
        return java.util.Collections.singletonList(new Object[]{categoria, ocurrencias, new BigDecimal(total)});
    }

    private CategoriaResumenResponse cat(String nombre, long ocurrencias) {
        return new CategoriaResumenResponse(
                1, nombre, "icon", "#000", new BigDecimal("1000"), ocurrencias, false);
    }

    @Test
    @DisplayName("Tres ocurrencias ya cuentan como gasto hormiga")
    void tresEsHormiga() {
        List<CategoriaResumenResponse> marcadas =
                service.marcarHormigas(List.of(cat("Delivery", 3)));

        assertThat(marcadas.get(0).hormiga()).isTrue();
    }

    @Test
    @DisplayName("Dos ocurrencias no alcanzan el umbral")
    void dosNoEsHormiga() {
        List<CategoriaResumenResponse> marcadas =
                service.marcarHormigas(List.of(cat("Hogar", 2)));

        assertThat(marcadas.get(0).hormiga()).isFalse();
    }

    @Test
    @DisplayName("El umbral sale de la configuracion, no esta hardcodeado")
    void umbralConfigurable() {
        assertThat(service.umbral()).isEqualTo(3);
    }

    @Test
    @DisplayName("detectar(): una categoría con 3 ocurrencias aparece como hormiga")
    void detectarConTresOcurrenciasEsHormiga() {
        when(gastoRepository.agruparPorCategoria(eq(usuarioId), eq(desde), eq(hasta), eq(false), anyBoolean()))
                .thenReturn(fila("Delivery", 3, "15000.00"));

        List<HormigaResponse> hormigas = service.detectar(usuarioId, periodo, false);

        assertThat(hormigas).hasSize(1);
        assertThat(hormigas.get(0).categoria()).isEqualTo("Delivery");
        assertThat(hormigas.get(0).ocurrencias()).isEqualTo(3);
        assertThat(hormigas.get(0).total()).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("detectar(): una categoría con 2 ocurrencias no aparece")
    void detectarConDosOcurrenciasNoEsHormiga() {
        when(gastoRepository.agruparPorCategoria(eq(usuarioId), eq(desde), eq(hasta), eq(false), anyBoolean()))
                .thenReturn(fila("Hogar", 2, "6000.00"));

        List<HormigaResponse> hormigas = service.detectar(usuarioId, periodo, false);

        assertThat(hormigas).isEmpty();
    }

    @Test
    @DisplayName("detectar(): la variación contra el promedio todavía viaja en null")
    void variacionEnNullPorAhora() {
        when(gastoRepository.agruparPorCategoria(eq(usuarioId), eq(desde), eq(hasta), eq(false), anyBoolean()))
                .thenReturn(fila("Delivery", 3, "15000.00"));

        List<HormigaResponse> hormigas = service.detectar(usuarioId, periodo, false);

        assertThat(hormigas.get(0).variacionVsPromedio()).isNull();
    }

    @Test
    @DisplayName("detectar(): con excluirCuotas=true (default), consulta sin cuotas (RN-02)")
    void consultaSinCuotasPorDefault() {
        when(gastoRepository.agruparPorCategoria(eq(usuarioId), eq(desde), eq(hasta), eq(false), eq(false)))
                .thenReturn(List.of());

        service.detectar(usuarioId, periodo, false);

        verify(gastoRepository).agruparPorCategoria(usuarioId, desde, hasta, false, false);
    }

    @Test
    @DisplayName("detectar(): con excluirCuotas=false, incluye las cuotas en el agrupado")
    void incluyeCuotasSiLaPropiedadLoPermite() {
        PocketProperties props = new PocketProperties();
        props.getHormiga().setExcluirCuotas(false);
        service = new HormigaServiceImpl(gastoRepository, props);
        when(gastoRepository.agruparPorCategoria(eq(usuarioId), eq(desde), eq(hasta), eq(true), eq(true)))
                .thenReturn(List.of());

        service.detectar(usuarioId, periodo, true);

        verify(gastoRepository).agruparPorCategoria(usuarioId, desde, hasta, true, true);
    }
}
