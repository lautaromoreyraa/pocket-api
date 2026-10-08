package com.pocket.service.demo;

import com.pocket.event.UsuarioCreado;

/**
 * Historia de ejemplo para un usuario recién creado.
 *
 * Solo tiene sentido en la demo pública: como la app es anónima por
 * dispositivo, cada visitante entra con una cuenta vacía y vería todas las
 * pantallas en cero, que es justo lo contrario de lo que una demo tiene que
 * mostrar. Sembrarle su propia historia le deja tocar, editar y borrar sin
 * afectar a los demás visitantes.
 */
public interface SembradorDemo {

    /** No hace nada si la siembra está desactivada (el caso normal). */
    void sembrarSiCorresponde(UsuarioCreado evento);
}
