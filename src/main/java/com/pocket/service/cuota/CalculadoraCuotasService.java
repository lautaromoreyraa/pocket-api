package com.pocket.service.cuota;

import com.pocket.domain.CompraFinanciada;
import com.pocket.domain.Gasto;

import java.util.List;

public interface CalculadoraCuotasService {

    List<Gasto> generarCuotas(CompraFinanciada compra);
}
