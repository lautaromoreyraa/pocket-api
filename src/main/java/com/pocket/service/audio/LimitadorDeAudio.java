package com.pocket.service.audio;

import com.pocket.domain.Usuario;

/**
 * Cupo diario de procesamientos de audio, por usuario y para todos juntos.
 *
 * Existe por la demo pública: procesar un audio consume cuota de un proveedor
 * externo que se agota, y el alta de usuario es anónima, así que nada impide
 * que un solo visitante deje sin la función a todos los que entren después.
 */
public interface LimitadorDeAudio {

    /**
     * Registra un procesamiento para el usuario y verifica que ni él ni el
     * conjunto de usuarios hayan excedido el cupo del día.
     *
     * @throws com.pocket.exception.LimiteDeAudioAlcanzadoException si se excedió alguno
     */
    void registrarUso(Usuario usuario);
}
