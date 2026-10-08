package com.pocket.service.gastofijo;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.domain.Gasto;
import com.pocket.domain.GastoFijo;
import com.pocket.domain.Usuario;
import com.pocket.dto.gasto.GastoResponse;
import com.pocket.dto.gastofijo.GastoFijoRequest;
import com.pocket.dto.gastofijo.GastoFijoResponse;
import com.pocket.dto.gastofijo.RegistroFijoRequest;
import com.pocket.dto.gastofijo.ResumenFijosResponse;
import com.pocket.enumeration.MedioPago;
import com.pocket.enumeration.OrigenGasto;
import com.pocket.exception.CategoriaNoEncontradaException;
import com.pocket.exception.GastoFijoNoEncontradoException;
import com.pocket.exception.OperacionNoPermitidaException;
import com.pocket.exception.PeriodoInvalidoException;
import com.pocket.exception.RegistroDeFijoNoEncontradoException;
import com.pocket.mapper.gasto.impl.GastoMapperImpl;
import com.pocket.mapper.gastofijo.impl.GastoFijoMapperImpl;
import com.pocket.repository.CategoriaRepository;
import com.pocket.repository.GastoFijoRepository;
import com.pocket.repository.GastoRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.gastofijo.impl.GastoFijoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GastoFijoServiceImplTest {

    private GastoFijoRepository gastoFijoRepository;
    private GastoRepository gastoRepository;
    private CategoriaRepository categoriaRepository;
    private AuthService authService;
    private GastoFijoServiceImpl service;

    private Usuario usuario;
    private Categoria servicios;
    private PocketProperties props;

    /** El mes en curso según la misma zona horaria que usa el servicio. */
    private YearMonth mesActual;

    @BeforeEach
    void setUp() {
        gastoFijoRepository = mock(GastoFijoRepository.class);
        gastoRepository = mock(GastoRepository.class);
        categoriaRepository = mock(CategoriaRepository.class);
        authService = mock(AuthService.class);
        props = new PocketProperties();

        service = new GastoFijoServiceImpl(
                gastoFijoRepository, gastoRepository, categoriaRepository,
                new GastoFijoMapperImpl(), new GastoMapperImpl(), authService, props);

        usuario = Usuario.builder().id(UUID.randomUUID()).deviceUuid("dev").build();
        servicios = categoria(5, "Servicios");
        mesActual = YearMonth.now(ZoneId.of(props.getPeriodo().getZonaHoraria()));

        lenient().when(authService.actual()).thenReturn(usuario);
        lenient().when(categoriaRepository.findById(5)).thenReturn(Optional.of(servicios));
        lenient().when(gastoFijoRepository.save(any(GastoFijo.class))).thenAnswer(inv -> {
            GastoFijo f = inv.getArgument(0);
            if (f.getId() == null) f.setId(UUID.randomUUID());
            return f;
        });
        lenient().when(gastoRepository.save(any(Gasto.class))).thenAnswer(inv -> {
            Gasto g = inv.getArgument(0);
            if (g.getId() == null) g.setId(UUID.randomUUID());
            return g;
        });
        lenient().when(gastoRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
    }

    // --- ABM de la plantilla ------------------------------------------------

    @Test
    @DisplayName("Un fijo nuevo nace activo aunque el request no diga nada del estado")
    void altaSinActivoNaceActivo() {
        GastoFijoResponse creado = service.registrar(request(null));

        assertThat(creado.activo()).isTrue();
        assertThat(creado.descripcion()).isEqualTo("Internet");
        assertThat(creado.diaDelMes()).isEqualTo(10);
        assertThat(creado.categoriaNombre()).isEqualTo("Servicios");
    }

    @Test
    @DisplayName("Pausar es una edición: el request de edición sí manda el estado")
    void edicionPuedePausar() {
        GastoFijo fijo = fijo(new BigDecimal("45000.00"), 10, true);
        when(gastoFijoRepository.findById(fijo.getId())).thenReturn(Optional.of(fijo));

        GastoFijoResponse editado = service.editar(fijo.getId(), request(false));

        assertThat(editado.activo()).isFalse();
    }

    @Test
    @DisplayName("Editar la plantilla no toca los meses ya pagados")
    void editarNoTocaLosGastosGenerados() {
        GastoFijo fijo = fijo(new BigDecimal("45000.00"), 10, true);
        when(gastoFijoRepository.findById(fijo.getId())).thenReturn(Optional.of(fijo));

        service.editar(fijo.getId(), request(true));

        // El aumento del alquiler no reescribe lo que ya se pagó: es historia (RF-44).
        verify(gastoRepository, never()).save(any(Gasto.class));
    }

    @Test
    @DisplayName("Al borrar la plantilla, los gastos ya generados se sueltan pero no se borran (RF-44)")
    void eliminarDesvinculaEnVezDeBorrarHistoria() {
        GastoFijo fijo = fijo(new BigDecimal("45000.00"), 10, true);
        when(gastoFijoRepository.findById(fijo.getId())).thenReturn(Optional.of(fijo));

        service.eliminar(fijo.getId());

        verify(gastoRepository).desvincularDelFijo(fijo.getId());
        verify(gastoFijoRepository).delete(fijo);
    }

    @Test
    @DisplayName("Un fijo de otro usuario es un 404, no un 403")
    void fijoAjenoEsNoEncontrado() {
        GastoFijo ajeno = fijo(new BigDecimal("1000.00"), 5, true);
        ajeno.setUsuario(Usuario.builder().id(UUID.randomUUID()).deviceUuid("otro").build());
        when(gastoFijoRepository.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));

        // Un 403 confirmaría que ese id existe.
        assertThatThrownBy(() -> service.eliminar(ajeno.getId()))
                .isInstanceOf(GastoFijoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Una categoría inexistente es un 404 de categoría, no una violación de FK")
    void categoriaInexistente() {
        when(categoriaRepository.findById(99)).thenReturn(Optional.empty());

        GastoFijoRequest req = new GastoFijoRequest(
                "Internet", new BigDecimal("45000.00"), 99, MedioPago.DEBITO, 10, null);

        assertThatThrownBy(() -> service.registrar(req))
                .isInstanceOf(CategoriaNoEncontradaException.class);
    }

    // --- El resumen del período ---------------------------------------------

    @Test
    @DisplayName("El estimado usa el monto real donde ya se pagó, y el de la plantilla donde no")
    void estimadoMezclaRealYPlantilla() {
        GastoFijo pagado = fijo(new BigDecimal("45000.00"), 10, true);
        GastoFijo pendiente = fijo(new BigDecimal("30000.00"), 20, true);
        when(gastoFijoRepository.findDelUsuario(usuario.getId()))
                .thenReturn(List.of(pagado, pendiente));
        // Se pagó más caro que lo que decía la plantilla: 52.300 en vez de 45.000.
        when(gastoRepository.findFijosMaterializados(eq(usuario.getId()), any(), any()))
                .thenReturn(List.of(materializado(pagado, new BigDecimal("52300.00"))));

        ResumenFijosResponse resumen = service.resumenDelPeriodo(mesActual);

        // 52.300 reales + 30.000 estimados. Sumar la plantilla del pagado haría
        // que el "te queda" de la pantalla mienta por 7.300.
        assertThat(resumen.totalEstimado()).isEqualByComparingTo("82300.00");
        assertThat(resumen.totalRegistrado()).isEqualByComparingTo("52300.00");
        assertThat(resumen.items()).hasSize(2);
        assertThat(resumen.items().get(0).gastoId()).isNotNull();
        assertThat(resumen.items().get(1).gastoId()).isNull();
        assertThat(resumen.items().get(1).montoRegistrado()).isNull();
    }

    @Test
    @DisplayName("Un fijo pausado no suma al estimado, pero si ya se pagó sí suma a lo registrado")
    void pausadoNoSumaAlEstimadoPeroSiALoPagado() {
        GastoFijo pausado = fijo(new BigDecimal("17000.00"), 15, false);
        when(gastoFijoRepository.findDelUsuario(usuario.getId())).thenReturn(List.of(pausado));
        when(gastoRepository.findFijosMaterializados(eq(usuario.getId()), any(), any()))
                .thenReturn(List.of(materializado(pausado, new BigDecimal("17000.00"))));

        ResumenFijosResponse resumen = service.resumenDelPeriodo(mesActual);

        // Si lo pagaste y después lo pausaste, la plata salió igual.
        assertThat(resumen.totalEstimado()).isEqualByComparingTo("0");
        assertThat(resumen.totalRegistrado()).isEqualByComparingTo("17000.00");
    }

    // --- Tildar --------------------------------------------------------------

    @Test
    @DisplayName("Tildar genera un gasto con origen FIJO y el monto que se pagó, no el de la plantilla")
    void registrarGeneraElGasto() {
        GastoFijo fijo = fijo(new BigDecimal("45000.00"), 10, true);
        when(gastoFijoRepository.findById(fijo.getId())).thenReturn(Optional.of(fijo));

        GastoResponse gasto = service.registrarDelPeriodo(
                fijo.getId(), new RegistroFijoRequest(mesActual, new BigDecimal("52300.00"), UUID.randomUUID()));

        assertThat(gasto.origen()).isEqualTo(OrigenGasto.FIJO);
        assertThat(gasto.monto()).isEqualByComparingTo("52300.00");
        assertThat(gasto.categoriaId()).isEqualTo(5);
        assertThat(gasto.compraFinanciadaId()).isNull();
    }

    @Test
    @DisplayName("Un fijo de crédito se imputa al mes siguiente, como cualquier gasto (RN-03)")
    void fijoDeCreditoSeImputaAlMesSiguiente() {
        GastoFijo fijo = fijo(new BigDecimal("12400.00"), 5, true);
        fijo.setMedioPago(MedioPago.CREDITO);
        when(gastoFijoRepository.findById(fijo.getId())).thenReturn(Optional.of(fijo));

        GastoResponse gasto = service.registrarDelPeriodo(
                fijo.getId(), new RegistroFijoRequest(mesActual, new BigDecimal("12400.00"), UUID.randomUUID()));

        assertThat(YearMonth.from(gasto.fechaGasto())).isEqualTo(mesActual);
        assertThat(YearMonth.from(gasto.fechaImputacion())).isEqualTo(mesActual.plusMonths(1));
    }

    @Test
    @DisplayName("Si el día de vencimiento todavía no llegó, la fecha del gasto es hoy y no una futura")
    void fechaNoPuedeSerFutura() {
        LocalDate hoy = LocalDate.now(ZoneId.of(props.getPeriodo().getZonaHoraria()));
        // Un vencimiento posterior a hoy dentro del mes en curso. Si hoy ya es
        // 28 o más, no existe ese caso y el día 28 cae en el pasado: el assert
        // de "no es futura" sigue siendo el que importa.
        GastoFijo fijo = fijo(new BigDecimal("9000.00"), 28, true);
        when(gastoFijoRepository.findById(fijo.getId())).thenReturn(Optional.of(fijo));

        GastoResponse gasto = service.registrarDelPeriodo(
                fijo.getId(), new RegistroFijoRequest(mesActual, new BigDecimal("9000.00"), UUID.randomUUID()));

        assertThat(gasto.fechaGasto()).isBeforeOrEqualTo(hoy);
    }

    @Test
    @DisplayName("En un mes ya cerrado la fecha del gasto es el día de vencimiento")
    void enMesCerradoUsaElDiaDeVencimiento() {
        GastoFijo fijo = fijo(new BigDecimal("9000.00"), 12, true);
        when(gastoFijoRepository.findById(fijo.getId())).thenReturn(Optional.of(fijo));
        YearMonth mesPasado = mesActual.minusMonths(1);

        GastoResponse gasto = service.registrarDelPeriodo(
                fijo.getId(), new RegistroFijoRequest(mesPasado, new BigDecimal("9000.00"), UUID.randomUUID()));

        assertThat(gasto.fechaGasto()).isEqualTo(mesPasado.atDay(12));
    }

    @Test
    @DisplayName("Tildar dos veces el mismo mes es un 409, no un duplicado silencioso")
    void dobleRegistroEnElMismoMes() {
        GastoFijo fijo = fijo(new BigDecimal("45000.00"), 10, true);
        when(gastoFijoRepository.findById(fijo.getId())).thenReturn(Optional.of(fijo));
        when(gastoRepository.existeRegistroDelFijo(eq(fijo.getId()), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.registrarDelPeriodo(
                fijo.getId(), new RegistroFijoRequest(mesActual, new BigDecimal("45000.00"), UUID.randomUUID())))
                .isInstanceOf(OperacionNoPermitidaException.class);

        verify(gastoRepository, never()).save(any(Gasto.class));
    }

    @Test
    @DisplayName("El reintento con la misma clave devuelve el gasto existente en vez de chocar contra el 409")
    void reintentoConLaMismaClaveEsIdempotente() {
        GastoFijo fijo = fijo(new BigDecimal("45000.00"), 10, true);
        UUID clave = UUID.randomUUID();
        Gasto yaGuardado = materializado(fijo, new BigDecimal("45000.00"));
        when(gastoRepository.findByIdempotencyKey(clave)).thenReturn(Optional.of(yaGuardado));

        GastoResponse gasto = service.registrarDelPeriodo(
                fijo.getId(), new RegistroFijoRequest(mesActual, new BigDecimal("45000.00"), clave));

        // Un reintento de red no tiene por qué chocar contra el 409 que él mismo
        // provocó: la idempotencia se evalúa antes que el "ya está registrado".
        assertThat(gasto.id()).isEqualTo(yaGuardado.getId());
        verify(gastoRepository, never()).save(any(Gasto.class));
    }

    @Test
    @DisplayName("Un fijo pausado no se puede tildar")
    void pausadoNoSePuedeRegistrar() {
        GastoFijo fijo = fijo(new BigDecimal("45000.00"), 10, false);
        when(gastoFijoRepository.findById(fijo.getId())).thenReturn(Optional.of(fijo));

        assertThatThrownBy(() -> service.registrarDelPeriodo(
                fijo.getId(), new RegistroFijoRequest(mesActual, new BigDecimal("45000.00"), UUID.randomUUID())))
                .isInstanceOf(OperacionNoPermitidaException.class);
    }

    @Test
    @DisplayName("No se puede tildar un mes que todavía no empezó")
    void periodoFuturoEsInvalido() {
        GastoFijo fijo = fijo(new BigDecimal("45000.00"), 10, true);
        when(gastoFijoRepository.findById(fijo.getId())).thenReturn(Optional.of(fijo));

        assertThatThrownBy(() -> service.registrarDelPeriodo(
                fijo.getId(),
                new RegistroFijoRequest(mesActual.plusMonths(1), new BigDecimal("45000.00"), UUID.randomUUID())))
                .isInstanceOf(PeriodoInvalidoException.class);
    }

    // --- Corregir el monto del mes -------------------------------------------

    @Test
    @DisplayName("Corregir el monto del mes cambia el gasto y deja la plantilla como estaba")
    void editarMontoNoTocaLaPlantilla() {
        GastoFijo fijo = fijo(new BigDecimal("45000.00"), 10, true);
        Gasto gasto = materializado(fijo, new BigDecimal("45000.00"));
        when(gastoFijoRepository.findById(fijo.getId())).thenReturn(Optional.of(fijo));
        when(gastoRepository.findRegistroDelFijo(eq(fijo.getId()), any(), any()))
                .thenReturn(Optional.of(gasto));

        GastoResponse corregido =
                service.editarMontoDelPeriodo(fijo.getId(), mesActual, new BigDecimal("52300.00"));

        assertThat(corregido.monto()).isEqualByComparingTo("52300.00");
        // La plantilla sigue diciendo lo que se espera pagar todos los meses.
        assertThat(fijo.getMonto()).isEqualByComparingTo("45000.00");
        verify(gastoFijoRepository, never()).save(any(GastoFijo.class));
    }

    @Test
    @DisplayName("Corregir el monto de un mes que no se tildó es un 404")
    void editarMontoSinRegistro() {
        GastoFijo fijo = fijo(new BigDecimal("45000.00"), 10, true);
        when(gastoFijoRepository.findById(fijo.getId())).thenReturn(Optional.of(fijo));
        when(gastoRepository.findRegistroDelFijo(eq(fijo.getId()), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.editarMontoDelPeriodo(fijo.getId(), mesActual, new BigDecimal("52300.00")))
                .isInstanceOf(RegistroDeFijoNoEncontradoException.class);
    }

    // --- Helpers -------------------------------------------------------------

    private GastoFijoRequest request(Boolean activo) {
        return new GastoFijoRequest(
                "Internet", new BigDecimal("45000.00"), 5, MedioPago.DEBITO, 10, activo);
    }

    private GastoFijo fijo(BigDecimal monto, int diaDelMes, boolean activo) {
        return GastoFijo.builder()
                .id(UUID.randomUUID())
                .usuario(usuario)
                .categoria(servicios)
                .descripcion("Internet")
                .monto(monto)
                .medioPago(MedioPago.DEBITO)
                .diaDelMes(diaDelMes)
                .activo(activo)
                .build();
    }

    /** El `gasto` que deja tildar un fijo. */
    private Gasto materializado(GastoFijo fijo, BigDecimal monto) {
        return Gasto.builder()
                .id(UUID.randomUUID())
                .usuario(usuario)
                .categoria(servicios)
                .gastoFijo(fijo)
                .idempotencyKey(UUID.randomUUID())
                .monto(monto)
                .descripcion(fijo.getDescripcion())
                .medioPago(fijo.getMedioPago())
                .origen(OrigenGasto.FIJO)
                .fechaGasto(fijo.vencimientoEn(mesActual))
                .fechaImputacion(mesActual.atDay(1))
                .build();
    }

    private Categoria categoria(int id, String nombre) {
        Categoria c = new Categoria();
        c.setId(id);
        c.setNombre(nombre);
        return c;
    }
}
