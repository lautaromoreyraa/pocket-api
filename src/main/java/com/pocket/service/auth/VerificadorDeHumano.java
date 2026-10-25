package com.pocket.service.auth;

/**
 * Comprueba que quien pide el alta de un dispositivo sea una persona en un
 * navegador y no un script.
 *
 * El alta es anónima, así que cada dispositivo nuevo es un usuario nuevo en la
 * base y un cupo nuevo de audio. Sin esta verificación, crearlos en serie no
 * cuesta nada.
 */
public interface VerificadorDeHumano {

    /**
     * @param token el que entregó el widget en el cliente; puede ser null
     * @throws com.pocket.exception.VerificacionHumanaFallidaException si no pasa
     */
    void verificar(String token);
}
