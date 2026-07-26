package com.pocket.service.hormiga;

import com.pocket.config.PocketProperties;
import com.pocket.dto.resumen.CategoriaResumenResponse;
import com.pocket.service.hormiga.impl.HormigaServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HormigaServiceImplTest {

    private final HormigaServiceImpl service = new HormigaServiceImpl(new PocketProperties());

    private CategoriaResumenResponse cat(String nombre, long ocurrencias) {
        return new CategoriaResumenResponse(
                1, nombre, "icon", "#000", new BigDecimal("1000"), ocurrencias, false);
    }

    @Test
    @DisplayName("Tres ocurrencias ya cuentan como gasto hormiga")
    void tresEsHormiga() {
        List<CategoriaResumenResponse> marcadas =
                service.marcarHormigas(List.of(cat("Delivery", 3)));

        assertThat(marcadas.get(0).hormiga()).isTrue();
    }

    @Test
    @DisplayName("Dos ocurrencias no alcanzan el umbral")
    void dosNoEsHormiga() {
        List<CategoriaResumenResponse> marcadas =
                service.marcarHormigas(List.of(cat("Hogar", 2)));

        assertThat(marcadas.get(0).hormiga()).isFalse();
    }

    @Test
    @DisplayName("El umbral sale de la configuracion, no esta hardcodeado")
    void umbralConfigurable() {
        assertThat(service.umbral()).isEqualTo(3);
    }
}
