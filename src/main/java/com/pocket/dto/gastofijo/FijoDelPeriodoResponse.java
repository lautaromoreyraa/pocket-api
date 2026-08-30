package com.pocket.dto.gastofijo;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * El estado de una plantilla dentro de un período concreto: el tilde de la
 * pantalla.
 *
 * `gastoId` no nulo significa "ya lo pagué este mes". No es un flag aparte
 * porque no hay un estado que mantener sincronizado: el tilde <b>es</b> la
 * existencia del gasto. Tildarlo lo crea, destildarlo lo borra, y por eso suma
 * al total del mes exactamente cuando está tildado, sin ninguna regla extra.
 */
public record FijoDelPeriodoResponse(
        GastoFijoResponse fijo,

        /** Id del `gasto` generado; null si todavía no se registró este mes. */
        UUID gastoId,

        /** Lo que realmente se registró, que puede diferir del monto de la
         *  plantilla. Null si todavía no se registró. */
        BigDecimal montoRegistrado
) {}
