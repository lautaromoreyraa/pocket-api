package com.pocket.dto.gastofijo;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * GET /api/gastos-fijos/resumen?periodo= — alimenta la pestaña Fijos entera.
 */
public record ResumenFijosResponse(
        YearMonth periodo,

        /** Activos y pausados: la pantalla los muestra en secciones distintas. */
        List<FijoDelPeriodoResponse> items,

        /**
         * Lo que se va a ir en fijos este mes, sumando solo los activos.
         *
         * Para cada uno se usa el monto <b>real</b> si ya se registró, y el de
         * la plantilla si todavía no. La pantalla muestra "Ingreso − Fijos = Te
         * queda": si la plantilla dice $40.000 y pagaste $52.000, sumar la
         * plantilla haría que ese "te queda" mienta por $12.000.
         */
        BigDecimal totalEstimado,

        /** Solo lo ya tildado. La diferencia contra el estimado es lo que falta pagar. */
        BigDecimal totalRegistrado
) {}
