package com.pocket.service.audio.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Usuario;
import com.pocket.exception.LimiteDeAudioAlcanzadoException;
import com.pocket.service.audio.LimitadorDeAudio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cupo diario en memoria del proceso.
 *
 * El estado no se persiste a propósito. Lo que se está protegiendo es una
 * cuota externa que también se resetea sola todos los días, y el costo de
 * equivocarse es que alguien procese un audio de más después de un reinicio.
 * Una tabla, su migración y su limpieza serían más infraestructura que la que
 * justifica el problema.
 *
 * Vale mientras corra una sola instancia, que es el caso del deploy de la demo.
 * Con varias réplicas cada una llevaría su propia cuenta y el tope efectivo
 * sería el límite por la cantidad de instancias; ahí correspondería moverlo a
 * la base o a un contador compartido.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LimitadorDeAudioEnMemoria implements LimitadorDeAudio {

    /** A partir de acá se purgan las entradas de días pasados. */
    private static final int USUARIOS_ANTES_DE_PURGAR = 5_000;

    private final PocketProperties props;

    private final Map<UUID, UsoDiario> usoPorUsuario = new ConcurrentHashMap<>();

    @Override
    public void registrarUso(Usuario usuario) {
        int limite = props.getIa().getLimiteDiarioPorUsuario();
        if (limite <= 0) {
            // Uso normal de la app: no hay cupo que administrar.
            return;
        }

        LocalDate hoy = LocalDate.now(ZoneId.of(props.getPeriodo().getZonaHoraria()));
        purgarSiHaceFalta(hoy);

        UsoDiario uso = usoPorUsuario.compute(usuario.getId(), (id, actual) ->
                actual == null || !actual.dia().equals(hoy)
                        ? new UsoDiario(hoy, new AtomicInteger())
                        : actual);

        int consumidos = uso.contador().incrementAndGet();
        if (consumidos > limite) {
            log.info("Usuario {} alcanzó el cupo diario de audios ({})", usuario.getId(), limite);
            throw new LimiteDeAudioAlcanzadoException(
                    "Alcanzaste el límite de " + limite + " audios por día de la demo. "
                            + "Podés seguir cargando gastos a mano.");
        }
    }

    /**
     * El mapa solo crece: un visitante nuevo por dispositivo es una entrada
     * nueva. Como las de días anteriores ya no deciden nada, se descartan
     * cuando la cantidad de usuarios lo justifica.
     */
    private void purgarSiHaceFalta(LocalDate hoy) {
        if (usoPorUsuario.size() > USUARIOS_ANTES_DE_PURGAR) {
            usoPorUsuario.values().removeIf(uso -> !uso.dia().equals(hoy));
        }
    }

    private record UsoDiario(LocalDate dia, AtomicInteger contador) {}
}
