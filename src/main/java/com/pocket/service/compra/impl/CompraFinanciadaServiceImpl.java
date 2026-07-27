package com.pocket.service.compra.impl;

import com.pocket.domain.Categoria;
import com.pocket.domain.CompraFinanciada;
import com.pocket.domain.Gasto;
import com.pocket.domain.Usuario;
import com.pocket.dto.compra.CompraEdicionRequest;
import com.pocket.dto.compra.CompraFinanciadaRequest;
import com.pocket.dto.compra.CompraFinanciadaResponse;
import com.pocket.dto.resumen.CuotaEnCursoResponse;
import com.pocket.exception.CategoriaNoEncontradaException;
import com.pocket.exception.CompraNoEncontradaException;
import com.pocket.mapper.compra.CompraFinanciadaMapper;
import com.pocket.repository.CategoriaRepository;
import com.pocket.repository.CompraFinanciadaRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.compra.CompraFinanciadaService;
import com.pocket.service.cuota.CalculadoraCuotasService;
import com.pocket.util.PeriodoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompraFinanciadaServiceImpl implements CompraFinanciadaService {

    private final CompraFinanciadaRepository compraRepository;
    private final CategoriaRepository categoriaRepository;
    private final CalculadoraCuotasService calculadoraCuotas;
    private final CompraFinanciadaMapper compraMapper;
    private final AuthService authService;

    @Override
    @Transactional
    public CompraFinanciadaResponse registrar(CompraFinanciadaRequest request) {
        // Idempotencia (RF-41): si ya se registró esta clave, devolvemos la existente.
        return compraRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(compraMapper::aResponse)
                .orElseGet(() -> crear(request));
    }

    private CompraFinanciadaResponse crear(CompraFinanciadaRequest request) {
        Usuario usuario = authService.actual();
        Categoria categoria = buscarCategoria(request.categoriaId());

        CompraFinanciada compra = CompraFinanciada.builder()
                .usuario(usuario)
                .idempotencyKey(request.idempotencyKey())
                .categoria(categoria)
                .descripcion(request.descripcion())
                .montoTotal(request.montoTotal())
                .cantidadCuotas(request.cantidadCuotas())
                .fechaCompra(request.fechaCompra())
                .build();

        // Las N cuotas salen de la calculadora (RN-03, RN-04): no reimplementamos el redondeo.
        List<Gasto> cuotas = calculadoraCuotas.generarCuotas(compra);
        compra.setCuotas(cuotas);

        try {
            // save en cascada persiste la compra y sus cuotas.
            return compraMapper.aResponse(compraRepository.save(compra));
        } catch (DataIntegrityViolationException e) {
            // Race de dos requests con la misma key: la otra ganó, devolvemos esa.
            return compraRepository.findByIdempotencyKey(request.idempotencyKey())
                    .map(compraMapper::aResponse)
                    .orElseThrow(() -> e);
        }
    }

    @Override
    @Transactional
    public CompraFinanciadaResponse editar(UUID id, CompraEdicionRequest request) {
        CompraFinanciada compra = buscarPropia(id);
        Categoria categoria = buscarCategoria(request.categoriaId());

        // Categoría y descripción se propagan a todas las cuotas. Montos, cantidad
        // de cuotas y fechas quedan intactos: cambiarlos rompería el invariante de
        // que las N cuotas suman el total.
        compra.setCategoria(categoria);
        compra.setDescripcion(request.descripcion());
        for (Gasto cuota : compra.getCuotas()) {
            cuota.setCategoria(categoria);
            cuota.setDescripcion(request.descripcion());
        }

        return compraMapper.aResponse(compraRepository.save(compra));
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        CompraFinanciada compra = buscarPropia(id);
        // El borrado en cascada de la FK (ON DELETE CASCADE + orphanRemoval) baja las cuotas.
        compraRepository.delete(compra);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuotaEnCursoResponse> cuotasEnCurso(YearMonth periodo) {
        Usuario usuario = authService.actual();
        List<CompraFinanciada> compras = compraRepository.findConCuotasPendientes(
                usuario.getId(), PeriodoUtil.primerDia(periodo));
        return compras.stream()
                .map(c -> compraMapper.aCuotaEnCurso(c, periodo))
                .toList();
    }

    /** Carga la compra y valida que pertenezca al usuario actual.
     *  Una compra ajena se trata como inexistente: un 403 confirmaría que ese id existe. */
    private CompraFinanciada buscarPropia(UUID id) {
        CompraFinanciada compra = compraRepository.findById(id)
                .orElseThrow(() -> new CompraNoEncontradaException(id));
        if (!compra.getUsuario().getId().equals(authService.actual().getId())) {
            throw new CompraNoEncontradaException(id);
        }
        return compra;
    }

    private Categoria buscarCategoria(Integer categoriaId) {
        return categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new CategoriaNoEncontradaException(categoriaId));
    }
}
