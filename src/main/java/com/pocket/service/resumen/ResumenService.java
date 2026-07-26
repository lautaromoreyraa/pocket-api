package com.pocket.service.resumen;

import com.pocket.dto.resumen.ResumenResponse;

import java.time.YearMonth;

/**
 * Arma el resumen de una pestaña (RF-28 a RF-31).
 *
 * El mismo método sirve para Débito, Crédito e Histórico: solo cambian
 * el período y el flag de crédito.
 */
public interface ResumenService {

    ResumenResponse armar(YearMonth periodo, boolean credito);
}
