package com.pocket.service.gastofijo;

import com.pocket.dto.gasto.GastoResponse;
import com.pocket.dto.gastofijo.GastoFijoRequest;
import com.pocket.dto.gastofijo.GastoFijoResponse;
import com.pocket.dto.gastofijo.RegistroFijoRequest;
import com.pocket.dto.gastofijo.ResumenFijosResponse;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Plantillas de gastos que se repiten todos los meses.
 *
 * Una plantilla no es un gasto y no suma a ningún total por sí misma: cada mes
 * se materializa como una fila de `gasto` con `origen = FIJO`, igual que una
 * compra financiada materializa sus cuotas (RF-22).
 */
public interface GastoFijoService {

    List<GastoFijoResponse> listar();

    GastoFijoResponse registrar(GastoFijoRequest request);

    GastoFijoResponse editar(UUID id, GastoFijoRequest request);

    /** Baja de la plantilla. Los gastos ya generados en meses anteriores no se
     *  tocan: son historia y se conserva íntegra (RF-44). */
    void eliminar(UUID id);

    /** Las plantillas con su estado dentro del período: qué está tildado y qué no. */
    ResumenFijosResponse resumenDelPeriodo(YearMonth periodo);

    /** Tilda el fijo del mes: materializa la plantilla como un `gasto` real. */
    GastoResponse registrarDelPeriodo(UUID id, RegistroFijoRequest request);

    /**
     * Corrige cuánto se pagó este mes, sin tocar nada más del gasto.
     *
     * Existe como endpoint propio en vez de resolverse con PUT /api/gastos/{id}
     * porque ese pide el gasto entero, y el cliente tendría que reconstruir
     * categoría, medio de pago y fecha desde la plantilla: si la plantilla se
     * editó después de tildar, corregir un monto le pisaría al gasto valores
     * que nadie quiso cambiar.
     */
    GastoResponse editarMontoDelPeriodo(UUID id, YearMonth periodo, BigDecimal monto);
}
