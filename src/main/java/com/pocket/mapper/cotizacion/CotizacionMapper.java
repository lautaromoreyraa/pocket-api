package com.pocket.mapper.cotizacion;

import com.pocket.domain.Cotizacion;
import com.pocket.dto.cotizacion.CotizacionResponse;

public interface CotizacionMapper {
    CotizacionResponse aResponse(Cotizacion cotizacion, boolean desdeCache);
}
