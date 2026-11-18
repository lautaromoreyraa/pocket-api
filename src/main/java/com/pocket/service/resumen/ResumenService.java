package com.pocket.service.resumen;

import com.pocket.dto.resumen.ResumenResponse;

import java.time.YearMonth;
import java.util.List;

public interface ResumenService {

    ResumenResponse armar(YearMonth periodo, boolean credito);

    List<YearMonth> periodosConDatos();
}
