package com.pocket.service.hormiga;

import com.pocket.dto.resumen.CategoriaResumenResponse;
import com.pocket.dto.resumen.HormigaResponse;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Detección de gasto hormiga (RN-01, RN-02). */
public interface HormigaService {

    /** Categorías que alcanzan el umbral de ocurrencias en el período. */
    List<HormigaResponse> detectar(UUID usuarioId, YearMonth periodo, boolean credito);

    /** Marca cuáles de las barras del gráfico deben pintarse en rojo (RF-27). */
    List<CategoriaResumenResponse> marcarHormigas(List<CategoriaResumenResponse> categorias);

    int umbral();
}
