package com.pocket.service.ingreso.impl;

import com.pocket.domain.Ingreso;
import com.pocket.domain.Usuario;
import com.pocket.dto.ingreso.IngresoRequest;
import com.pocket.dto.ingreso.IngresoResponse;
import com.pocket.exception.IngresoNoEncontradoException;
import com.pocket.mapper.ingreso.IngresoMapper;
import com.pocket.repository.IngresoRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.ingreso.IngresoService;
import com.pocket.util.PeriodoUtil;
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
        Usuario usuario = authService.actual();

        Ingreso ingreso = Ingreso.builder()
                .usuario(usuario)
                .monto(request.monto())
                .descripcion(request.descripcion())
                // El período llega como mes; en base se guarda como el día 1.
                .periodo(PeriodoUtil.primerDia(request.periodo()))
                .build();

        return ingresoMapper.aResponse(ingresoRepository.save(ingreso));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngresoResponse> listarDelPeriodo(YearMonth periodo) {
        Usuario usuario = authService.actual();
        List<Ingreso> ingresos = ingresoRepository.findByUsuarioIdAndPeriodo(
                usuario.getId(), PeriodoUtil.primerDia(periodo));
        return ingresoMapper.aResponse(ingresos);
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        Ingreso ingreso = ingresoRepository.findById(id)
                .orElseThrow(() -> new IngresoNoEncontradoException(id));
        // Ingreso ajeno = inexistente: un 403 confirmaría que ese id existe.
        if (!ingreso.getUsuario().getId().equals(authService.actual().getId())) {
            throw new IngresoNoEncontradoException(id);
        }
        ingresoRepository.delete(ingreso);
    }
}
