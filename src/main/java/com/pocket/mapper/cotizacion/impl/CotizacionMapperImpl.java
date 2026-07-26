package com.pocket.mapper.cotizacion.impl;

import com.pocket.domain.Cotizacion;
import com.pocket.dto.cotizacion.CotizacionResponse;
import com.pocket.mapper.cotizacion.CotizacionMapper;
import org.springframework.stereotype.Component;

@Component
public class CotizacionMapperImpl implements CotizacionMapper {

    @Override
    public CotizacionResponse aResponse(Cotizacion c, boolean desdeCache) {
        if (c == null) return null;
        return new CotizacionResponse(
                c.getValorCompra(), c.getValorVenta(), c.getFechaActualizacion(), desdeCache);
    }
}
