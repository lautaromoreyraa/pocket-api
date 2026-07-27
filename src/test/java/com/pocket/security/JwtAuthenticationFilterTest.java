package com.pocket.security;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Usuario;
import com.pocket.repository.UsuarioRepository;
import com.pocket.security.jwt.impl.JwtServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String SECRETO =
            "secreto-solo-para-tests-no-usar-en-ningun-otro-lado-1234567890";

    private JwtServiceImpl jwtService;
    private UsuarioRepository usuarioRepository;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        PocketProperties props = new PocketProperties();
        props.getSecurity().getJwt().setSecret(SECRETO);
        jwtService = new JwtServiceImpl(props);

        usuarioRepository = mock(UsuarioRepository.class);
        filter = new JwtAuthenticationFilter(jwtService, usuarioRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Usuario usuarioCon(UUID id) {
        return Usuario.builder().id(id).deviceUuid("device-" + id).build();
    }

    @Test
    @DisplayName("Un Bearer valido puebla el SecurityContext con el usuario del token")
    void bearerValidoPoblaContexto() throws Exception {
        UUID id = UUID.randomUUID();
        Usuario usuario = usuarioCon(id);
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));
        String token = jwtService.generar(usuario);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isSameAs(usuario);
        // La cadena siempre continua.
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("Sin header Authorization el contexto queda vacio y la cadena sigue")
    void sinHeaderNoAutentica() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("Un token invalido no autentica")
    void tokenInvalidoNoAutentica() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer no-es-un-jwt");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
