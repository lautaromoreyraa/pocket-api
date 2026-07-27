package com.pocket.service.gasto.impl;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.domain.Gasto;
import com.pocket.domain.Usuario;
import com.pocket.dto.gasto.GastoRequest;
import com.pocket.dto.gasto.GastoResponse;
import com.pocket.enumeration.MedioPago;
import com.pocket.enumeration.OrigenGasto;
import com.pocket.exception.CategoriaNoEncontradaException;
import com.pocket.exception.GastoNoEncontradoException;
import com.pocket.exception.OperacionNoPermitidaException;
import com.pocket.mapper.gasto.GastoMapper;
import com.pocket.repository.CategoriaRepository;
import com.pocket.repository.GastoRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.gasto.GastoService;
import com.pocket.util.PeriodoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GastoServiceImpl implements GastoService {

    private static final String MENSAJE_CUOTA =
            "Esta cuota se gestiona desde la compra financiada; " +
            "eliminá la compra para dar de baja sus cuotas.";

    private final GastoRepository gastoRepository;
    private final CategoriaRepository categoriaRepository;
    private final GastoMapper gastoMapper;
    private final AuthService authService;
    private final PocketProperties props;

    @Override
    @Transactional
    public GastoResponse registrar(GastoRequest request, OrigenGasto origen) {
        // Idempotencia (RF-41): si ya se registró esta clave, devolvemos el existente.
        return gastoRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(gastoMapper::aResponse)
                .orElseGet(() -> crear(request, origen));
    }

    private GastoResponse crear(GastoRequest request, OrigenGasto origen) {
        Usuario usuario = authService.actual();
        Categoria categoria = buscarCategoria(request.categoriaId());

        Gasto gasto = Gasto.builder()
                .usuario(usuario)
                .categoria(categoria)
                .idempotencyKey(request.idempotencyKey())
                .monto(request.monto())
                .descripcion(request.descripcion())
                .medioPago(request.medioPago())
                .origen(origen)
                .fechaGasto(request.fechaGasto())
                .fechaImputacion(imputacion(request.fechaGasto(), request.medioPago()))
                .build();

        return gastoMapper.aResponse(gastoRepository.save(gasto));
    }

    @Override
    @Transactional
    public List<GastoResponse> registrarLote(List<GastoRequest> requests, OrigenGasto origen) {
        return requests.stream().map(r -> registrar(r, origen)).toList();
    }

    @Override
    @Transactional
    public GastoResponse editar(UUID id, GastoRequest request) {
        Gasto gasto = buscarPropio(id);

        // Una cuota se gestiona desde la compra: editarla sola rompería el
        // invariante de que las N cuotas suman el total y dejaría la compra
        // con cuotas en categorías distintas (RF-25).
        if (gasto.esCuota()) {
            throw new OperacionNoPermitidaException(MENSAJE_CUOTA);
        }

        // La idempotencyKey del request se ignora: la clave del gasto no se toca.
        gasto.setMonto(request.monto());
        gasto.setCategoria(buscarCategoria(request.categoriaId()));
        gasto.setDescripcion(request.descripcion());
        gasto.setMedioPago(request.medioPago());
        gasto.setFechaGasto(request.fechaGasto());
        // Recalcular la imputación: si pasó de débito a crédito, se mueve al mes siguiente.
        gasto.setFechaImputacion(imputacion(request.fechaGasto(), request.medioPago()));

        // El promedio y las hormigas salen por consulta, no hay nada que recalcular (RF-14).
        return gastoMapper.aResponse(gastoRepository.save(gasto));
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        Gasto gasto = buscarPropio(id);

        if (gasto.esCuota()) {
            throw new OperacionNoPermitidaException(MENSAJE_CUOTA);
        }

        gastoRepository.delete(gasto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GastoResponse> listarDelPeriodo(YearMonth periodo, boolean credito) {
        Usuario usuario = authService.actual();
        List<Gasto> gastos = gastoRepository.findDelPeriodo(
                usuario.getId(),
                PeriodoUtil.primerDia(periodo),
                PeriodoUtil.ultimoDia(periodo),
                credito);
        return gastoMapper.aResponse(gastos);
    }

    /** Carga el gasto y valida que pertenezca al usuario actual.
     *  Un gasto ajeno se trata como inexistente: un 403 confirmaría que ese id existe. */
    private Gasto buscarPropio(UUID id) {
        Gasto gasto = gastoRepository.findById(id)
                .orElseThrow(() -> new GastoNoEncontradoException(id));
        if (!gasto.getUsuario().getId().equals(authService.actual().getId())) {
            throw new GastoNoEncontradoException(id);
        }
        return gasto;
    }

    private Categoria buscarCategoria(Integer categoriaId) {
        return categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new CategoriaNoEncontradaException(categoriaId));
    }

    private LocalDate imputacion(LocalDate fechaGasto, MedioPago medioPago) {
        return PeriodoUtil.periodoDeImputacion(
                fechaGasto, medioPago, props.getCuotas().getDesfasePrimeraCuota()).atDay(1);
    }
}
