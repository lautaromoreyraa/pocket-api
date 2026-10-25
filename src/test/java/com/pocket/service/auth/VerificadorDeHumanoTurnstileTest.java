package com.pocket.service.auth;

import java.util.Map;

import com.pocket.config.PocketProperties;
import com.pocket.exception.VerificacionHumanaFallidaException;
import com.pocket.service.auth.impl.VerificadorDeHumanoTurnstile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VerificadorDeHumanoTurnstileTest {

    private static final String URL = "https://turnstile.test/siteverify";

    private PocketProperties props;
    private MockRestServiceServer servidor;
    private VerificadorDeHumanoTurnstile verificador;

    @BeforeEach
    void setUp() {
        props = new PocketProperties();
        props.getTurnstile().setSecreto("secreto-de-prueba");

        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
        verificador = new VerificadorDeHumanoTurnstile(props, builder.build());
    }

    @Test
    @DisplayName("Sin secreto la verificación está apagada y no llama a Cloudflare")
    void sinSecretoNoVerifica() {
        props.getTurnstile().setSecreto(null);

        assertThatCode(() -> verificador.verificar(null)).doesNotThrowAnyException();
        servidor.verify();
    }

    @Test
    @DisplayName("Con secreto, un alta sin token se rechaza sin llamar a Cloudflare")
    void sinTokenSeRechaza() {
        assertThatThrownBy(() -> verificador.verificar(" "))
                .isInstanceOf(VerificacionHumanaFallidaException.class);
        servidor.verify();
    }

    @Test
    @DisplayName("Un token válido pasa, y viajan el secreto y el token")
    void tokenValidoPasa() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of(
                        "secret", "secreto-de-prueba",
                        "response", "token-bueno")))
                .andRespond(withSuccess("{\"success\":true,\"error-codes\":[]}",
                        MediaType.APPLICATION_JSON));

        assertThatCode(() -> verificador.verificar("token-bueno")).doesNotThrowAnyException();
        servidor.verify();
    }

    @Test
    @DisplayName("Un token que Cloudflare rechaza no pasa")
    void tokenRechazadoNoPasa() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> verificador.verificar("token-reusado"))
                .isInstanceOf(VerificacionHumanaFallidaException.class);
    }

    @Test
    @DisplayName("Si Cloudflare falla, el alta se rechaza: ante la duda, cerrado")
    void cloudflareCaidoNoPasa() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> verificador.verificar("token"))
                .isInstanceOf(VerificacionHumanaFallidaException.class);
    }
}
