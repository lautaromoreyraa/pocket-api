package com.pocket.service.cotizacion;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Cotizacion;
import com.pocket.dto.cotizacion.CotizacionApiResponse;
import com.pocket.dto.cotizacion.CotizacionResponse;
import com.pocket.exception.CotizacionNoDisponibleException;
import com.pocket.mapper.cotizacion.impl.CotizacionMapperImpl;
import com.pocket.repository.CotizacionRepository;
import com.pocket.service.cotizacion.impl.CotizacionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CotizacionServiceImplTest {

    private RestClient cotizacionRestClient;
    private RestClient.RequestHeadersUriSpec uriSpec;
    private RestClient.RequestHeadersSpec requestSpec;
    private RestClient.ResponseSpec responseSpec;
    private CotizacionRepository cotizacionRepository;
    private CotizacionServiceImpl service;

    private final PocketProperties props = new PocketProperties();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cotizacionRestClient = mock(RestClient.class);
        uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        requestSpec = mock(RestClient.RequestHeadersSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);
        cotizacionRepository = mock(CotizacionRepository.class);

        props.getCotizacion().setUrl("https://dolarapi.com/v1/dolares/blue");
        props.getMoneda().setRefrescoCotizacionMinutos(30);

        when(cotizacionRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(props.getCotizacion().getUrl())).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);

        service = new CotizacionServiceImpl(
                cotizacionRestClient, cotizacionRepository, new CotizacionMapperImpl(), props);
    }

    private void apiResponde(String compra, String venta) {
        when(responseSpec.body(CotizacionApiResponse.class))
                .thenReturn(new CotizacionApiResponse(new BigDecimal(compra), new BigDecimal(venta)));
    }

    private void apiFalla() {
        when(responseSpec.body(CotizacionApiResponse.class))
                .thenThrow(new RestClientException("timeout"));
    }

    private Cotizacion cotizacionGuardada(String compra, String venta, Instant fechaActualizacion) {
        return Cotizacion.builder()
                .id(1)
                .valorCompra(new BigDecimal(compra))
                .valorVenta(new BigDecimal(venta))
                .fechaActualizacion(fechaActualizacion)
                .build();
    }

    // --- refrescar() nunca lanza ---------------------------------------

    @Test
    @DisplayName("refrescar(): si la API falla, no propaga la excepción")
    void refrescarNoPropagaSiLaApiFalla() {
        apiFalla();

        service.refrescar();

        verify(cotizacionRepository, never()).save(any());
    }

    @Test
    @DisplayName("refrescar(): si la API devuelve compra o venta nulos, no guarda y no propaga")
    void refrescarNoGuardaConBodyIncompleto() {
        when(responseSpec.body(CotizacionApiResponse.class))
                .thenReturn(new CotizacionApiResponse(null, new BigDecimal("1200")));

        service.refrescar();

        verify(cotizacionRepository, never()).save(any());
    }

    @Test
    @DisplayName("refrescar(): si la API devuelve un body nulo, no guarda y no propaga")
    void refrescarNoGuardaConBodyNulo() {
        when(responseSpec.body(CotizacionApiResponse.class)).thenReturn(null);

        service.refrescar();

        verify(cotizacionRepository, never()).save(any());
    }

    @Test
    @DisplayName("refrescar(): con éxito, guarda compra y venta en la misma fila existente")
    void refrescarActualizaLaFilaExistente() {
        Cotizacion existente = cotizacionGuardada("1200.00", "1250.00", Instant.now().minus(1, ChronoUnit.HOURS));
        when(cotizacionRepository.findFirstByOrderByFechaActualizacionDesc()).thenReturn(Optional.of(existente));
        apiResponde("1300.00", "1350.00");

        service.refrescar();

        verify(cotizacionRepository).save(existente);
        assertThat(existente.getId()).isEqualTo(1); // misma fila, no una nueva
        assertThat(existente.getValorCompra()).isEqualByComparingTo("1300.00");
        assertThat(existente.getValorVenta()).isEqualByComparingTo("1350.00");
    }

    @Test
    @DisplayName("refrescar(): sin fila previa, inserta una nueva")
    void refrescarInsertaSiNoHabiaNinguna() {
        when(cotizacionRepository.findFirstByOrderByFechaActualizacionDesc()).thenReturn(Optional.empty());
        apiResponde("1300.00", "1350.00");

        service.refrescar();

        verify(cotizacionRepository).save(any(Cotizacion.class));
    }

    // --- obtenerBlue() ----------------------------------------------------

    @Test
    @DisplayName("Una cotización vigente no dispara una llamada nueva a la API")
    void cotizacionVigenteNoLlamaALaApi() {
        Cotizacion vigente = cotizacionGuardada("1200.00", "1250.00", Instant.now().minus(5, ChronoUnit.MINUTES));
        when(cotizacionRepository.findFirstByOrderByFechaActualizacionDesc()).thenReturn(Optional.of(vigente));

        CotizacionResponse resp = service.obtenerBlue();

        assertThat(resp.desdeCache()).isFalse();
        assertThat(resp.compra()).isEqualByComparingTo("1200.00");
        verifyNoInteractions(cotizacionRestClient);
    }

    @Test
    @DisplayName("Cotización vencida + API responde: refresca y devuelve el valor nuevo, desdeCache=false")
    void cotizacionVencidaConApiOk() {
        Cotizacion vencida = cotizacionGuardada("1200.00", "1250.00", Instant.now().minus(1, ChronoUnit.HOURS));
        Cotizacion actualizada = cotizacionGuardada("1300.00", "1350.00", Instant.now());
        when(cotizacionRepository.findFirstByOrderByFechaActualizacionDesc())
                .thenReturn(Optional.of(vencida))
                .thenReturn(Optional.of(actualizada));
        apiResponde("1300.00", "1350.00");

        CotizacionResponse resp = service.obtenerBlue();

        assertThat(resp.desdeCache()).isFalse();
        assertThat(resp.compra()).isEqualByComparingTo("1300.00");
    }

    @Test
    @DisplayName("Con la API caída y una cotización guardada, obtenerBlue devuelve esa con desdeCache=true")
    void apiCaidaConCotizacionGuardadaDevuelveDesdeCache() {
        Cotizacion vencida = cotizacionGuardada("1200.00", "1250.00", Instant.now().minus(1, ChronoUnit.HOURS));
        when(cotizacionRepository.findFirstByOrderByFechaActualizacionDesc()).thenReturn(Optional.of(vencida));
        apiFalla();

        CotizacionResponse resp = service.obtenerBlue();

        assertThat(resp.desdeCache()).isTrue();
        assertThat(resp.compra()).isEqualByComparingTo("1200.00");
    }

    @Test
    @DisplayName("Con la API caída y sin ninguna cotización guardada, devuelve 503")
    void apiCaidaSinCotizacionGuardadaDevuelve503() {
        when(cotizacionRepository.findFirstByOrderByFechaActualizacionDesc()).thenReturn(Optional.empty());
        apiFalla();

        assertThatThrownBy(() -> service.obtenerBlue())
                .isInstanceOf(CotizacionNoDisponibleException.class);
    }

    @Test
    @DisplayName("Sin cotización guardada y API ok: la guarda y la devuelve, desdeCache=false")
    void sinCotizacionGuardadaConApiOk() {
        Cotizacion nueva = cotizacionGuardada("1300.00", "1350.00", Instant.now());
        when(cotizacionRepository.findFirstByOrderByFechaActualizacionDesc())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(nueva));
        apiResponde("1300.00", "1350.00");

        CotizacionResponse resp = service.obtenerBlue();

        assertThat(resp.desdeCache()).isFalse();
        assertThat(resp.compra()).isEqualByComparingTo("1300.00");
    }
}
