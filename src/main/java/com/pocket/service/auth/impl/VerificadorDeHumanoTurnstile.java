package com.pocket.service.auth.impl;

import com.pocket.config.PocketProperties;
import com.pocket.dto.turnstile.RespuestaDeTurnstile;
import com.pocket.exception.VerificacionHumanaFallidaException;
import com.pocket.service.auth.VerificadorDeHumano;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerificadorDeHumanoTurnstile implements VerificadorDeHumano {

    private static final String MENSAJE =
            "No pudimos verificar que seas una persona. Recargá la página e intentá de nuevo.";

    private final PocketProperties props;
    private final RestClient turnstileRestClient;

    @Override
    public void verificar(String token) {
        String secreto = props.getTurnstile().getSecreto();
        if (!StringUtils.hasText(secreto)) {
            return;
        }
        if (!StringUtils.hasText(token)) {
            throw new VerificacionHumanaFallidaException(MENSAJE);
        }

        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("secret", secreto);
        formulario.add("response", token);

        RespuestaDeTurnstile respuesta;
        try {
            respuesta = turnstileRestClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formulario)
                    .retrieve()
                    .body(RespuestaDeTurnstile.class);
        } catch (RestClientException e) {
            log.warn("Turnstile no respondió: {}", e.getMessage());
            throw new VerificacionHumanaFallidaException(MENSAJE);
        }

        if (respuesta == null || !respuesta.success()) {
            log.info("Turnstile rechazó un alta: {}",
                    respuesta == null ? "sin respuesta" : respuesta.codigosDeError());
            throw new VerificacionHumanaFallidaException(MENSAJE);
        }
    }
}
