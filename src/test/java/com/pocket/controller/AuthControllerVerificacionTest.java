package com.pocket.controller;

import com.pocket.exception.VerificacionHumanaFallidaException;
import com.pocket.repository.UsuarioRepository;
import com.pocket.service.auth.VerificadorDeHumano;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerVerificacionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private VerificadorDeHumano verificadorDeHumano;

    @Test
    @DisplayName("Un dispositivo nuevo que no pasa la verificación da 403 y no se crea")
    void dispositivoNuevoSinVerificarNoSeCrea() throws Exception {
        doThrow(new VerificacionHumanaFallidaException("no"))
                .when(verificadorDeHumano).verificar(any());

        mockMvc.perform(post("/api/auth/dispositivo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceUuid\":\"script-1\",\"turnstileToken\":\"falso\"}"))
                .andExpect(status().isForbidden());

        assertThat(usuarioRepository.findByDeviceUuid("script-1")).isEmpty();
    }

    @Test
    @DisplayName("Un dispositivo que ya existe entra sin verificación")
    void dispositivoExistenteNoSeVerifica() throws Exception {
        String cuerpo = "{\"deviceUuid\":\"vuelve-1\"}";
        mockMvc.perform(post("/api/auth/dispositivo")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());

        doThrow(new VerificacionHumanaFallidaException("no"))
                .when(verificadorDeHumano).verificar(any());

        // La app pide el alta en cada apertura: quien vuelve no resuelve otro
        // desafío ni depende de que Cloudflare conteste.
        mockMvc.perform(post("/api/auth/dispositivo")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("El token del cuerpo es el que llega al verificador")
    void elTokenLlegaAlVerificador() throws Exception {
        mockMvc.perform(post("/api/auth/dispositivo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceUuid\":\"nuevo-2\",\"turnstileToken\":\"tok-123\"}"))
                .andExpect(status().isOk());

        verify(verificadorDeHumano).verificar("tok-123");
        verify(verificadorDeHumano, never()).verificar("otro");
    }
}
