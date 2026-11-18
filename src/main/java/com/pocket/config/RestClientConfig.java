package com.pocket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient iaRestClient(PocketProperties props) {
        return RestClient.builder()
                .baseUrl(props.getIa().getUrlBase())
                .requestFactory(factory(
                        props.getIa().getConexionTimeoutSegundos(),
                        props.getIa().getTimeoutSegundos()))
                .build();
    }

    @Bean
    public RestClient groqRestClient(PocketProperties props) {
        return RestClient.builder()
                .baseUrl(props.getIa().getGroq().getUrlBase())
                .requestFactory(factory(
                        props.getIa().getConexionTimeoutSegundos(),
                        props.getIa().getTimeoutSegundos()))
                .build();
    }

    @Bean
    public RestClient turnstileRestClient(PocketProperties props) {
        return RestClient.builder()
                .baseUrl(props.getTurnstile().getUrl())
                .requestFactory(factory(
                        props.getTurnstile().getTimeoutSegundos(),
                        props.getTurnstile().getTimeoutSegundos()))
                .build();
    }

    @Bean
    public RestClient cotizacionRestClient(PocketProperties props) {
        return RestClient.builder()
                .requestFactory(factory(
                        props.getCotizacion().getTimeoutSegundos(),
                        props.getCotizacion().getTimeoutSegundos()))
                .build();
    }

    private SimpleClientHttpRequestFactory factory(int conexionSegundos, int lecturaSegundos) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(conexionSegundos));
        f.setReadTimeout(Duration.ofSeconds(lecturaSegundos));
        return f;
    }
}
