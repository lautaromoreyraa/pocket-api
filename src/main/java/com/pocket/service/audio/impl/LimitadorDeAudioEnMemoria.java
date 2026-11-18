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

@Slf4j
@Component
@RequiredArgsConstructor
public class LimitadorDeAudioEnMemoria implements LimitadorDeAudio {

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
            return;
        }

        LocalDate hoy = LocalDate.now(ZoneId.of(props.getPeriodo().getZonaHoraria()));

        AtomicInteger delUsuario = null;
        if (limite > 0) {
            delUsuario = consumirDelUsuario(usuario, hoy, limite);
        }

        if (limiteGlobal > 0 && !consumirGlobal(hoy, limiteGlobal)) {
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

    private void purgarSiHaceFalta(LocalDate hoy) {
        if (usoPorUsuario.size() > USUARIOS_ANTES_DE_PURGAR) {
            usoPorUsuario.values().removeIf(uso -> !uso.dia().equals(hoy));
        }
    }

    private record UsoDiario(LocalDate dia, AtomicInteger contador) {}
}
