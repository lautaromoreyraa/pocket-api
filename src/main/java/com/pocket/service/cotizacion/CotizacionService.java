package com.pocket.service.cotizacion;

import com.pocket.dto.cotizacion.CotizacionResponse;

public interface CotizacionService {

    CotizacionResponse obtenerBlue();

    void refrescar();
}
