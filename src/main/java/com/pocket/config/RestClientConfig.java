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
                .requestFactory(factory(
                        props.getIa().getConexionTimeoutSegundos(),
                        props.getIa().getTimeoutSegundos()))
                .build();
    }

    /**
     * Cliente para Groq. Se crea siempre, aunque el proveedor activo sea otro:
     * un RestClient sin usar no cuesta nada y evita condicionar la config.
     */
    @Bean
    public RestClient groqRestClient(PocketProperties props) {
        return RestClient.builder()
                .baseUrl(props.getIa().getGroq().getUrlBase())
                .requestFactory(factory(
                        props.getIa().getConexionTimeoutSegundos(),
                        props.getIa().getTimeoutSegundos()))
                .build();
    }

    /** Cliente para la API de cotización del blue (RF-38). */
    @Bean
    public RestClient cotizacionRestClient(PocketProperties props) {
        return RestClient.builder()
                .requestFactory(factory(
                        props.getCotizacion().getTimeoutSegundos(),
                        props.getCotizacion().getTimeoutSegundos()))
                .build();
    }

    /**
     * Conectar y leer se cortan por separado: conectar es rápido o no va a
     * serlo nunca, mientras que esperar a que el modelo genere la respuesta
     * lleva decenas de segundos.
     */
    private SimpleClientHttpRequestFactory factory(int conexionSegundos, int lecturaSegundos) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(conexionSegundos));
        f.setReadTimeout(Duration.ofSeconds(lecturaSegundos));
        return f;
    }
}
