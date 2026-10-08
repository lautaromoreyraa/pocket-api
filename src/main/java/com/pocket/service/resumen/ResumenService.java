package com.pocket.service.resumen;

import com.pocket.dto.resumen.ResumenResponse;

import java.time.YearMonth;
import java.util.List;

/**
 * Arma el resumen de una pestaña (RF-28 a RF-31).
 *
 * El mismo método sirve para Débito, Crédito e Histórico: solo cambian
 * el período y el flag de crédito.
 */
public interface ResumenService {

    ResumenResponse armar(YearMonth periodo, boolean credito);

    /** RF-45 — meses con al menos un gasto, del más viejo al más nuevo.
     *  Alimenta el selector de la pestaña Histórico. */
    List<YearMonth> periodosConDatos();
}
