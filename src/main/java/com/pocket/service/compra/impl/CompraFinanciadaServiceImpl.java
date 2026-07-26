package com.pocket.service.compra.impl;

import com.pocket.dto.compra.CompraFinanciadaRequest;
import com.pocket.dto.compra.CompraFinanciadaResponse;
import com.pocket.dto.resumen.CuotaEnCursoResponse;
import com.pocket.mapper.compra.CompraFinanciadaMapper;
import com.pocket.repository.CompraFinanciadaRepository;
import com.pocket.repository.GastoRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.compra.CompraFinanciadaService;
import com.pocket.service.cuota.CalculadoraCuotasService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompraFinanciadaServiceImpl implements CompraFinanciadaService {

    private final CompraFinanciadaRepository compraRepository;
    private final GastoRepository gastoRepository;
    private final CalculadoraCuotasService calculadoraCuotas;
    private final CompraFinanciadaMapper compraMapper;
    private final AuthService authService;

    @Override
    @Transactional
    public CompraFinanciadaResponse registrar(CompraFinanciadaRequest request) {
        // TODO: 1) armar la CompraFinanciada
        //       2) generar las N cuotas con CalculadoraCuotasService
        //       3) persistir en cascada
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        // TODO: el borrado en cascada de la FK ya elimina las cuotas.
        //       Confirmar con el usuario ocurre en el cliente (RF-13).
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public List<CuotaEnCursoResponse> cuotasEnCurso(YearMonth periodo) {
        throw new UnsupportedOperationException("Pendiente de implementar");
    }
}
