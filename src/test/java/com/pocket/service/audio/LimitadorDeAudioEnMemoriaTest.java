package com.pocket.service.audio;

import java.util.UUID;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Usuario;
import com.pocket.exception.LimiteDeAudioAlcanzadoException;
import com.pocket.service.audio.impl.LimitadorDeAudioEnMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitadorDeAudioEnMemoriaTest {

    private PocketProperties props;
    private LimitadorDeAudioEnMemoria limitador;

    private final Usuario usuario = usuarioNuevo();
    private final Usuario otro = usuarioNuevo();

    @BeforeEach
    void setUp() {
        props = new PocketProperties();
        limitador = new LimitadorDeAudioEnMemoria(props);
    }

    @Test
    @DisplayName("Con el límite en 0 no hay tope: es el uso normal de la app")
    void sinLimiteNoRestringe() {
        props.getIa().setLimiteDiarioPorUsuario(0);

        assertThatCode(() -> {
            for (int i = 0; i < 50; i++) {
                limitador.registrarUso(usuario);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Se permiten exactamente los usos del límite y el siguiente falla")
    void topeaEnElLimite() {
        props.getIa().setLimiteDiarioPorUsuario(2);

        limitador.registrarUso(usuario);
        limitador.registrarUso(usuario);

        assertThatThrownBy(() -> limitador.registrarUso(usuario))
                .isInstanceOf(LimiteDeAudioAlcanzadoException.class)
                .hasMessageContaining("2");
    }

    @Test
    @DisplayName("El cupo es por usuario: que uno lo agote no afecta a otro")
    void elCupoNoSeCompartePorUsuario() {
        props.getIa().setLimiteDiarioPorUsuario(1);

        limitador.registrarUso(usuario);
        assertThatThrownBy(() -> limitador.registrarUso(usuario))
                .isInstanceOf(LimiteDeAudioAlcanzadoException.class);

        // El segundo visitante arranca con su cupo intacto: si no, el primero
        // que entra a la demo dejaría sin audio a todos los demás.
        assertThatCode(() -> limitador.registrarUso(otro)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Insistir después de agotar el cupo sigue fallando, no lo reinicia")
    void insistirNoReiniciaElCupo() {
        props.getIa().setLimiteDiarioPorUsuario(1);
        limitador.registrarUso(usuario);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> limitador.registrarUso(usuario))
                    .isInstanceOf(LimiteDeAudioAlcanzadoException.class);
        }
    }

    private static Usuario usuarioNuevo() {
        return Usuario.builder().id(UUID.randomUUID()).deviceUuid("dev-" + UUID.randomUUID()).build();
    }
}
