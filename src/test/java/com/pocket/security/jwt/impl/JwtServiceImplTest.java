package com.pocket.security.jwt.impl;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceImplTest {

    private static final String SECRETO =
            "secreto-solo-para-tests-no-usar-en-ningun-otro-lado-1234567890";

    private PocketProperties props;
    private JwtServiceImpl service;

    @BeforeEach
    void setUp() {
        props = propsConSecreto(SECRETO);
        service = new JwtServiceImpl(props);
    }

    private PocketProperties propsConSecreto(String secreto) {
        PocketProperties p = new PocketProperties();
        p.getSecurity().getJwt().setSecret(secreto);
        return p;
    }

    private Usuario usuarioCon(UUID id) {
        return Usuario.builder().id(id).deviceUuid("device-" + id).build();
    }

    @Test
    @DisplayName("Un token recien generado es valido")
    void tokenGeneradoEsValido() {
        String token = service.generar(usuarioCon(UUID.randomUUID()));

        assertThat(service.esValido(token)).isTrue();
    }

    @Test
    @DisplayName("El id del usuario sobrevive la ida y vuelta por el token")
    void idViajaEnElSubject() {
        UUID id = UUID.randomUUID();
        String token = service.generar(usuarioCon(id));

        assertThat(service.extraerUsuarioId(token)).isEqualTo(id);
    }

    @Test
    @DisplayName("Un token con basura no es valido")
    void tokenMalformadoNoEsValido() {
        assertThat(service.esValido("no-es-un-jwt")).isFalse();
    }

    @Test
    @DisplayName("Un token firmado con otro secreto no es valido")
    void tokenConFirmaAjenaNoEsValido() {
        JwtServiceImpl otro = new JwtServiceImpl(
                propsConSecreto("otro-secreto-distinto-igual-de-largo-0987654321-abcdef"));
        String tokenAjeno = otro.generar(usuarioCon(UUID.randomUUID()));

        assertThat(service.esValido(tokenAjeno)).isFalse();
    }

    @Test
    @DisplayName("Un token de otro emisor no es valido")
    void tokenDeOtroEmisorNoEsValido() {
        PocketProperties otrosProps = propsConSecreto(SECRETO);
        otrosProps.getSecurity().getJwt().setEmisor("otro-emisor");
        JwtServiceImpl otro = new JwtServiceImpl(otrosProps);
        String tokenAjeno = otro.generar(usuarioCon(UUID.randomUUID()));

        assertThat(service.esValido(tokenAjeno)).isFalse();
    }

    @Test
    @DisplayName("Un token expirado no es valido")
    void tokenExpiradoNoEsValido() {
        PocketProperties vencidos = propsConSecreto(SECRETO);
        vencidos.getSecurity().getJwt().setExpiracionDias(-1);
        JwtServiceImpl emisorVencido = new JwtServiceImpl(vencidos);
        String token = emisorVencido.generar(usuarioCon(UUID.randomUUID()));

        assertThat(service.esValido(token)).isFalse();
    }

    @Test
    @DisplayName("Un secreto de menos de 32 bytes es rechazado con mensaje claro")
    void secretoCortoRompeConMensajeClaro() {
        PocketProperties debiles = propsConSecreto("corto");

        assertThatThrownBy(() -> new JwtServiceImpl(debiles))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pocket.security.jwt.secret");
    }
}
