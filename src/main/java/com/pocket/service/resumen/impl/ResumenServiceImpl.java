package com.pocket.service.resumen.impl;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.domain.Usuario;
import com.pocket.dto.resumen.BalanceResponse;
import com.pocket.dto.resumen.CategoriaResumenResponse;
import com.pocket.dto.resumen.CuotaEnCursoResponse;
import com.pocket.dto.resumen.HormigaResponse;
import com.pocket.dto.resumen.ResumenResponse;
import com.pocket.repository.GastoRepository;
import com.pocket.repository.IngresoRepository;
import com.pocket.service.auth.AuthService;
import com.pocket.service.compra.CompraFinanciadaService;
import com.pocket.service.hormiga.HormigaService;
import com.pocket.service.resumen.ResumenService;
import com.pocket.util.PeriodoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

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
        Usuario usuario = authService.actual();
        UUID usuarioId = usuario.getId();
        LocalDate desde = PeriodoUtil.primerDia(periodo);
        LocalDate hasta = PeriodoUtil.ultimoDia(periodo);

        BigDecimal total = gastoRepository.totalDelPeriodo(usuarioId, desde, hasta, credito);

        List<CategoriaResumenResponse> porCategoria = hormigaService.marcarHormigas(
                gastoRepository.agruparPorCategoria(usuarioId, desde, hasta, credito, true).stream()
                        .map(this::aCategoriaResumen)
                        .toList());

        List<HormigaResponse> hormigas = hormigaService.detectar(usuarioId, periodo, credito);

        List<CuotaEnCursoResponse> cuotasEnCurso =
                credito ? compraService.cuotasEnCurso(periodo) : List.of();

        BalanceResponse balance = armarBalance(usuarioId, desde, hasta);

        return new ResumenResponse(periodo, credito, total, porCategoria, hormigas, cuotasEnCurso, balance);
    }

    // Capacidad de ahorro global (RN-06): se muestra igual en débito y en crédito.
    private BalanceResponse armarBalance(UUID usuarioId, LocalDate desde, LocalDate hasta) {
        BigDecimal ingresos = ingresoRepository.totalDelPeriodo(usuarioId, desde);
        BigDecimal gastosDebito = gastoRepository.totalDelPeriodo(usuarioId, desde, hasta, false);
        BigDecimal gastosCredito = gastoRepository.totalDelPeriodo(usuarioId, desde, hasta, true);
        // Cargar un ingreso en $0 sigue siendo "cargó ingresos" (RF-33): el flag
        // se basa en la existencia del registro, no en si la suma da positivo.
        boolean tieneIngresos = ingresoRepository.existsByUsuarioIdAndPeriodo(usuarioId, desde);
        BigDecimal capacidadAhorro = ingresos.subtract(gastosDebito).subtract(gastosCredito);

        // Promedio histórico pendiente de implementar.
        return new BalanceResponse(
                ingresos, gastosDebito, gastosCredito, capacidadAhorro, tieneIngresos,
                null, false, false);
    }

    private CategoriaResumenResponse aCategoriaResumen(Object[] fila) {
        Categoria categoria = (Categoria) fila[0];
        long ocurrencias = (long) fila[1];
        BigDecimal total = (BigDecimal) fila[2];
        return new CategoriaResumenResponse(
                categoria.getId(), categoria.getNombre(), categoria.getIcono(), categoria.getColor(),
                total, ocurrencias, false);
    }
}
