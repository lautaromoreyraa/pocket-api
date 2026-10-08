package com.pocket.service.gastofijo.impl;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.domain.Gasto;
import com.pocket.domain.GastoFijo;
import com.pocket.domain.Usuario;
import com.pocket.dto.gasto.GastoResponse;
import com.pocket.dto.gastofijo.FijoDelPeriodoResponse;
import com.pocket.dto.gastofijo.GastoFijoRequest;
import com.pocket.dto.gastofijo.GastoFijoResponse;
import com.pocket.dto.gastofijo.RegistroFijoRequest;
import com.pocket.dto.gastofijo.ResumenFijosResponse;
import com.pocket.enumeration.OrigenGasto;
import com.pocket.exception.CategoriaNoEncontradaException;
import com.pocket.exception.GastoFijoNoEncontradoException;
import com.pocket.exception.OperacionNoPermitidaException;
import com.pocket.exception.PeriodoInvalidoException;
import com.pocket.exception.RegistroDeFijoNoEncontradoException;
import com.pocket.mapper.gasto.GastoMapper;
import com.pocket.mapper.gastofijo.GastoFijoMapper;
import com.pocket.repository.CategoriaRepository;
import com.pocket.repository.GastoFijoRepository;
import com.pocket.repository.GastoRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.gastofijo.GastoFijoService;
import com.pocket.util.PeriodoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GastoFijoServiceImpl implements GastoFijoService {

    private final GastoFijoRepository gastoFijoRepository;
    private final GastoRepository gastoRepository;
    private final CategoriaRepository categoriaRepository;
    private final GastoFijoMapper gastoFijoMapper;
    private final GastoMapper gastoMapper;
    private final AuthService authService;
    private final PocketProperties props;

    @Override
    @Transactional(readOnly = true)
    public List<GastoFijoResponse> listar() {
        return gastoFijoMapper.aResponse(
                gastoFijoRepository.findDelUsuario(authService.actual().getId()));
    }

    @Override
    @Transactional
    public GastoFijoResponse registrar(GastoFijoRequest request) {
        GastoFijo fijo = GastoFijo.builder()
                .usuario(authService.actual())
                .categoria(buscarCategoria(request.categoriaId()))
                .descripcion(request.descripcion())
                .monto(request.monto())
                .medioPago(request.medioPago())
                .diaDelMes(request.diaDelMes())
                .activo(request.activoOPorDefecto())
                .build();

        return gastoFijoMapper.aResponse(gastoFijoRepository.save(fijo));
    }

    @Override
    @Transactional
    public GastoFijoResponse editar(UUID id, GastoFijoRequest request) {
        GastoFijo fijo = buscarPropio(id);

        // Se edita la PLANTILLA, no los meses ya pagados. Si el alquiler sube,
        // los recibos anteriores siguen diciendo lo que se pagó: son historia
        // (RF-44) y reescribirlos borraría el aumento en vez de mostrarlo.
        fijo.setCategoria(buscarCategoria(request.categoriaId()));
        fijo.setDescripcion(request.descripcion());
        fijo.setMonto(request.monto());
        fijo.setMedioPago(request.medioPago());
        fijo.setDiaDelMes(request.diaDelMes());
        fijo.setActivo(request.activoOPorDefecto());

        return gastoFijoMapper.aResponse(gastoFijoRepository.save(fijo));
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        GastoFijo fijo = buscarPropio(id);

        // Los gastos ya generados NO se borran: la plata salió, y el mes pasado
        // no cambia porque hoy des-contrates el servicio (RF-44). Se los
        // desvincula a mano en vez de dejarlo en un ON DELETE SET NULL porque
        // MySQL no admite esa acción sobre una columna que además está en un
        // CHECK, y porque así el que lee este método ve qué pasa con la historia.
        //
        // Siguen diciendo `origen = FIJO`: fueron un fijo cuando ocurrieron, y
        // eso no deja de ser cierto porque la plantilla ya no exista.
        gastoRepository.desvincularDelFijo(fijo.getId());

        gastoFijoRepository.delete(fijo);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenFijosResponse resumenDelPeriodo(YearMonth periodo) {
        UUID usuarioId = authService.actual().getId();

        List<GastoFijo> fijos = gastoFijoRepository.findDelUsuario(usuarioId);
        Map<UUID, Gasto> registrados = registradosDelPeriodo(usuarioId, periodo);

        List<FijoDelPeriodoResponse> items = fijos.stream()
                .map(fijo -> {
                    Gasto gasto = registrados.get(fijo.getId());
                    return new FijoDelPeriodoResponse(
                            gastoFijoMapper.aResponse(fijo),
                            gasto == null ? null : gasto.getId(),
                            gasto == null ? null : gasto.getMonto());
                })
                .toList();

        // El estimado usa el monto real donde ya lo hay: la pantalla muestra
        // "Ingreso − Fijos = Te queda" y sumar la plantilla cuando pagaste otra
        // cosa haría que ese número mienta.
        BigDecimal totalEstimado = items.stream()
                .filter(item -> item.fijo().activo())
                .map(item -> item.montoRegistrado() == null ? item.fijo().monto() : item.montoRegistrado())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Lo registrado suma también lo de las plantillas pausadas: si pagaste
        // el fijo y después lo pausaste, la plata salió igual.
        BigDecimal totalRegistrado = items.stream()
                .map(FijoDelPeriodoResponse::montoRegistrado)
                .filter(monto -> monto != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ResumenFijosResponse(periodo, items, totalEstimado, totalRegistrado);
    }

    @Override
    @Transactional
    public GastoResponse registrarDelPeriodo(UUID id, RegistroFijoRequest request) {
        // Idempotencia (RF-41): el reintento exacto devuelve el mismo gasto en
        // vez de crear otro. Va antes que todo lo demás porque un reintento no
        // tiene por qué chocar con el 409 de "ya está registrado" que él mismo
        // provocó.
        var existente = gastoRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existente.isPresent()) {
            return gastoMapper.aResponse(existente.get());
        }

        GastoFijo fijo = buscarPropio(id);
        YearMonth periodo = request.periodo();

        if (!fijo.isActivo()) {
            throw new OperacionNoPermitidaException(
                    "El gasto fijo está pausado: activalo antes de registrarlo.");
        }

        YearMonth mesActual = PeriodoUtil.mesActual(props.getPeriodo().getZonaHoraria());
        if (periodo.isAfter(mesActual)) {
            throw new PeriodoInvalidoException(
                    "No se puede registrar un gasto fijo de un mes que todavía no empezó.");
        }

        if (gastoRepository.existeRegistroDelFijo(
                fijo.getId(), PeriodoUtil.primerDia(periodo), PeriodoUtil.ultimoDia(periodo))) {
            throw new OperacionNoPermitidaException(
                    "Este gasto fijo ya se registró en el período " + periodo + ".");
        }

        LocalDate fechaGasto = fechaDelRegistro(fijo, periodo, mesActual);

        Gasto gasto = Gasto.builder()
                .usuario(fijo.getUsuario())
                .categoria(fijo.getCategoria())
                .gastoFijo(fijo)
                .idempotencyKey(request.idempotencyKey())
                .monto(request.monto())
                .descripcion(fijo.getDescripcion())
                .medioPago(fijo.getMedioPago())
                .origen(OrigenGasto.FIJO)
                .fechaGasto(fechaGasto)
                // RN-03 y RN-10 salen gratis: un fijo materializado es un gasto
                // común, así que si es crédito se imputa al mes siguiente y
                // aparece en la pestaña que le corresponde, sin reglas propias.
                .fechaImputacion(PeriodoUtil.periodoDeImputacion(
                        fechaGasto, fijo.getMedioPago(),
                        props.getCuotas().getDesfasePrimeraCuota()).atDay(1))
                .build();

        return gastoMapper.aResponse(gastoRepository.save(gasto));
    }

    @Override
    @Transactional
    public GastoResponse editarMontoDelPeriodo(UUID id, YearMonth periodo, BigDecimal monto) {
        GastoFijo fijo = buscarPropio(id);

        Gasto gasto = gastoRepository.findRegistroDelFijo(
                        fijo.getId(), PeriodoUtil.primerDia(periodo), PeriodoUtil.ultimoDia(periodo))
                .orElseThrow(() -> new RegistroDeFijoNoEncontradoException(fijo.getId(), periodo));

        // Solo el monto. La fecha, la categoría y el medio de pago quedan como
        // estaban: lo que se corrige acá es cuánto se pagó, no qué se pagó.
        gasto.setMonto(monto);

        return gastoMapper.aResponse(gastoRepository.save(gasto));
    }

    /**
     * Cuándo ocurrió el gasto.
     *
     * El día de vencimiento de la plantilla, salvo que todavía no haya llegado:
     * si hoy es 16 y el fijo vence el 28, pagarlo hoy es un hecho de hoy, y una
     * fecha futura sería inconsistente con el resto del sistema —los gastos
     * sueltos ni siquiera la admiten (`@PastOrPresent`)—. En un mes ya cerrado
     * el tope no aplica y queda el día de vencimiento.
     */
    private LocalDate fechaDelRegistro(GastoFijo fijo, YearMonth periodo, YearMonth mesActual) {
        LocalDate vencimiento = fijo.vencimientoEn(periodo);
        if (!periodo.equals(mesActual)) {
            return vencimiento;
        }
        LocalDate hoy = LocalDate.now(ZoneId.of(props.getPeriodo().getZonaHoraria()));
        return vencimiento.isAfter(hoy) ? hoy : vencimiento;
    }

    /** Los gastos del período indexados por la plantilla que los generó. */
    private Map<UUID, Gasto> registradosDelPeriodo(UUID usuarioId, YearMonth periodo) {
        return gastoRepository.findFijosMaterializados(
                        usuarioId, PeriodoUtil.primerDia(periodo), PeriodoUtil.ultimoDia(periodo))
                .stream()
                .collect(Collectors.toMap(
                        gasto -> gasto.getGastoFijo().getId(), Function.identity()));
    }

    /** Carga la plantilla y valida que sea del usuario actual. Una ajena se trata
     *  como inexistente: un 403 confirmaría que ese id existe. */
    private GastoFijo buscarPropio(UUID id) {
        GastoFijo fijo = gastoFijoRepository.findById(id)
                .orElseThrow(() -> new GastoFijoNoEncontradoException(id));
        if (!fijo.getUsuario().getId().equals(authService.actual().getId())) {
            throw new GastoFijoNoEncontradoException(id);
        }
        return fijo;
    }

    private Categoria buscarCategoria(Integer categoriaId) {
        return categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new CategoriaNoEncontradaException(categoriaId));
    }
}
