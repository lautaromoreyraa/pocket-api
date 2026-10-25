package com.pocket.service.audio.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    private final AtomicReference<UsoDiario> usoGlobal =
            new AtomicReference<>(new UsoDiario(LocalDate.MIN, new AtomicInteger()));

    @Override
    public void registrarUso(Usuario usuario) {
        int limite = props.getIa().getLimiteDiarioPorUsuario();
        int limiteGlobal = props.getIa().getLimiteDiarioGlobal();
        if (limite <= 0 && limiteGlobal <= 0) {
            // Uso normal de la app: no hay cupo que administrar.
            return;
        }

        LocalDate hoy = LocalDate.now(ZoneId.of(props.getPeriodo().getZonaHoraria()));

        // El del usuario va primero: quien ya agotó el suyo no tiene que
        // descontarle nada al cupo de todos los demás.
        AtomicInteger delUsuario = null;
        if (limite > 0) {
            delUsuario = consumirDelUsuario(usuario, hoy, limite);
        }

        if (limiteGlobal > 0 && !consumirGlobal(hoy, limiteGlobal)) {
            // El audio no llega al proveedor, así que al usuario no se le
            // cobra: si el tope global se levanta (otro día, otro valor), su
            // cupo tiene que estar intacto.
            if (delUsuario != null) {
                delUsuario.decrementAndGet();
            }
            log.warn("Se alcanzó el cupo diario global de audios ({})", limiteGlobal);
            throw new LimiteDeAudioAlcanzadoException(
                    "La demo ya procesó todos los audios que puede por hoy. "
                            + "Podés seguir cargando gastos a mano.");
        }
    }

    private AtomicInteger consumirDelUsuario(Usuario usuario, LocalDate hoy, int limite) {
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
        return uso.contador();
    }

    /**
     * El tope por usuario solo no alcanza: el alta es anónima, así que con
     * crear dispositivos nuevos se consiguen cupos nuevos sin límite. Este es
     * el que de verdad protege la cuota externa.
     *
     * A diferencia del de usuario, el rechazo no suma: se descuenta solo si
     * hay lugar, para que insistir con la demo agotada no deje el contador
     * por encima del tope.
     */
    private boolean consumirGlobal(LocalDate hoy, int limiteGlobal) {
        UsoDiario uso = usoGlobal.updateAndGet(actual ->
                actual.dia().equals(hoy) ? actual : new UsoDiario(hoy, new AtomicInteger()));
        AtomicInteger contador = uso.contador();
        int consumidos;
        do {
            consumidos = contador.get();
            if (consumidos >= limiteGlobal) {
                return false;
            }
        } while (!contador.compareAndSet(consumidos, consumidos + 1));
        return true;
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
