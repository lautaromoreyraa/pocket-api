package com.pocket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Clientes HTTP para las integraciones externas.
 * RestClient viene incluido en spring-boot-starter-web desde 3.2:
 * no hace falta sumar WebFlux solo para tener WebClient.
 */
@Configuration
public class RestClientConfig {

    /** Cliente para el proveedor de IA (RF-05). */
    @Bean
    public RestClient iaRestClient(PocketProperties props) {
        return RestClient.builder()
                .baseUrl(props.getIa().getUrlBase())
                .requestFactory(factory(props.getIa().getTimeoutSegundos()))
                .build();
    }

    /** Cliente para la API de cotización del blue (RF-38). */
    @Bean
    public RestClient cotizacionRestClient(PocketProperties props) {
        return RestClient.builder()
                .requestFactory(factory(props.getCotizacion().getTimeoutSegundos()))
                .build();
    }

    private SimpleClientHttpRequestFactory factory(int segundos) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(segundos));
        f.setReadTimeout(Duration.ofSeconds(segundos));
        return f;
    }
}
