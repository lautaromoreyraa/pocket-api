package com.pocket.service.ingreso.impl;

import com.pocket.dto.ingreso.IngresoRequest;
import com.pocket.dto.ingreso.IngresoResponse;
import com.pocket.mapper.ingreso.IngresoMapper;
import com.pocket.repository.IngresoRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.ingreso.IngresoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IngresoServiceImpl implements IngresoService {

    private final IngresoRepository ingresoRepository;
    private final IngresoMapper ingresoMapper;
    private final AuthService authService;

    @Override
    @Transactional
    public IngresoResponse registrar(IngresoRequest request) {
        // TODO: normalizar el período al día 1 con PeriodoUtil.normalizarPeriodo
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public List<IngresoResponse> listarDelPeriodo(YearMonth periodo) {
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        throw new UnsupportedOperationException("Pendiente de implementar");
    }
}
