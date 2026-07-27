package com.pocket.controller;

import com.pocket.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("POST /api/auth/dispositivo devuelve un JWT tipo Bearer")
    void identificarDevuelveToken() throws Exception {
        mockMvc.perform(post("/api/auth/dispositivo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceUuid\":\"abc-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is(notNullValue())))
                .andExpect(jsonPath("$.tipo", is("Bearer")))
                .andExpect(jsonPath("$.cuentaVinculada", is(false)));
    }

    @Test
    @DisplayName("El mismo deviceUuid reutiliza el usuario, no crea uno nuevo")
    void mismoDispositivoMismoUsuario() throws Exception {
        String cuerpo = "{\"deviceUuid\":\"repetido-999\"}";

        mockMvc.perform(post("/api/auth/dispositivo")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/dispositivo")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());

        assertThat(usuarioRepository.findByDeviceUuid("repetido-999")).isPresent();
        assertThat(usuarioRepository.findAll().stream()
                .filter(u -> "repetido-999".equals(u.getDeviceUuid()))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("deviceUuid en blanco es rechazado con 400")
    void deviceUuidEnBlancoEsInvalido() throws Exception {
        mockMvc.perform(post("/api/auth/dispositivo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceUuid\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Un endpoint protegido sin token devuelve 401")
    void sinTokenDa401() throws Exception {
        mockMvc.perform(get("/api/gastos"))
                .andExpect(status().isUnauthorized());
    }

}
