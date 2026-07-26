package com.pocket.service.resumen.impl;

import com.pocket.config.PocketProperties;
import com.pocket.dto.resumen.ResumenResponse;
import com.pocket.repository.GastoRepository;
import com.pocket.repository.IngresoRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.compra.CompraFinanciadaService;
import com.pocket.service.hormiga.HormigaService;
import com.pocket.service.resumen.ResumenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class ResumenServiceImpl implements ResumenService {

    private final GastoRepository gastoRepository;
    private final IngresoRepository ingresoRepository;
    private final HormigaService hormigaService;
    private final CompraFinanciadaService compraService;
    private final AuthService authService;
    private final PocketProperties props;

    @Override
    public ResumenResponse armar(YearMonth periodo, boolean credito) {
        // TODO: 1) total del período
        //       2) agrupado por categoría, marcando hormigas (RF-27)
        //       3) cuotas en curso si credito = true
        //       4) balance: ahorro GLOBAL = ingresos - (débito + crédito) (RN-06)
        //       5) promedio histórico solo si hay más de 2 meses (RN-05)
        throw new UnsupportedOperationException("Pendiente de implementar");
    }
}
