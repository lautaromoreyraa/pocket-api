package com.pocket.service.hormiga;

import com.pocket.dto.resumen.CategoriaResumenResponse;
import com.pocket.dto.resumen.HormigaResponse;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface HormigaService {

    List<HormigaResponse> detectar(UUID usuarioId, YearMonth periodo, boolean credito);

    List<CategoriaResumenResponse> marcarHormigas(List<CategoriaResumenResponse> categorias);

    int umbral();
}
