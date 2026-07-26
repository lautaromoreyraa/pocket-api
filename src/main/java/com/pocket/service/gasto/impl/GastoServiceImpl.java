package com.pocket.service.gasto.impl;

import com.pocket.config.PocketProperties;
import com.pocket.dto.gasto.GastoRequest;
import com.pocket.dto.gasto.GastoResponse;
import com.pocket.enumeration.OrigenGasto;
import com.pocket.mapper.gasto.GastoMapper;
import com.pocket.repository.CategoriaRepository;
import com.pocket.repository.GastoRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.gasto.GastoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GastoServiceImpl implements GastoService {

    private final GastoRepository gastoRepository;
    private final CategoriaRepository categoriaRepository;
    private final GastoMapper gastoMapper;
    private final AuthService authService;
    private final PocketProperties props;

    @Override
    @Transactional
    public GastoResponse registrar(GastoRequest request, OrigenGasto origen) {
        // TODO: 1) buscar por idempotencyKey; si existe, devolver ese (RF-41)
        //       2) calcular fechaImputacion con PeriodoUtil.periodoDeImputacion (RN-03)
        //       3) persistir y mapear
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    @Transactional
    public List<GastoResponse> registrarLote(List<GastoRequest> requests, OrigenGasto origen) {
        return requests.stream().map(r -> registrar(r, origen)).toList();
    }

    @Override
    @Transactional
    public GastoResponse editar(UUID id, GastoRequest request) {
        // TODO: al cambiar monto o categoría, el promedio y las hormigas se
        //       recalculan solos porque no están materializados (RF-14).
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        // TODO: si el gasto es una cuota, la baja se maneja desde
        //       CompraFinanciadaService, no desde acá (RF-25).
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public List<GastoResponse> listarDelPeriodo(YearMonth periodo, boolean credito) {
        throw new UnsupportedOperationException("Pendiente de implementar");
    }
}
