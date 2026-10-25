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

    @Test
    @DisplayName("El cupo global corta aunque cada usuario sea nuevo")
    void elCupoGlobalCortaAunqueSeanUsuariosNuevos() {
        props.getIa().setLimiteDiarioPorUsuario(3);
        props.getIa().setLimiteDiarioGlobal(2);

        // Así se esquivaba el tope por usuario: un dispositivo nuevo por audio.
        limitador.registrarUso(usuarioNuevo());
        limitador.registrarUso(usuarioNuevo());

        assertThatThrownBy(() -> limitador.registrarUso(usuarioNuevo()))
                .isInstanceOf(LimiteDeAudioAlcanzadoException.class)
                .hasMessageContaining("todos los audios");
    }

    @Test
    @DisplayName("El cupo global funciona solo, sin tope por usuario")
    void elCupoGlobalFuncionaSinTopePorUsuario() {
        props.getIa().setLimiteDiarioGlobal(1);

        limitador.registrarUso(usuario);

        assertThatThrownBy(() -> limitador.registrarUso(otro))
                .isInstanceOf(LimiteDeAudioAlcanzadoException.class);
    }

    @Test
    @DisplayName("Un rechazo del cupo global no le gasta el cupo al usuario")
    void elRechazoGlobalNoGastaElCupoDelUsuario() {
        props.getIa().setLimiteDiarioPorUsuario(2);
        props.getIa().setLimiteDiarioGlobal(1);

        limitador.registrarUso(otro);
        assertThatThrownBy(() -> limitador.registrarUso(usuario))
                .isInstanceOf(LimiteDeAudioAlcanzadoException.class);

        // Con más lugar en el global, el usuario conserva sus dos audios.
        props.getIa().setLimiteDiarioGlobal(10);
        limitador.registrarUso(usuario);
        limitador.registrarUso(usuario);
        assertThatThrownBy(() -> limitador.registrarUso(usuario))
                .isInstanceOf(LimiteDeAudioAlcanzadoException.class)
                .hasMessageContaining("2 audios");
    }

    @Test
    @DisplayName("Quien ya agotó su cupo no le descuenta nada al global")
    void elRechazoPorUsuarioNoGastaElGlobal() {
        props.getIa().setLimiteDiarioPorUsuario(1);
        props.getIa().setLimiteDiarioGlobal(2);

        limitador.registrarUso(usuario);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> limitador.registrarUso(usuario))
                    .isInstanceOf(LimiteDeAudioAlcanzadoException.class);
        }

        assertThatCode(() -> limitador.registrarUso(otro)).doesNotThrowAnyException();
    }

    private static Usuario usuarioNuevo() {
        return Usuario.builder().id(UUID.randomUUID()).deviceUuid("dev-" + UUID.randomUUID()).build();
    }
}
