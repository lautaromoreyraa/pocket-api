package com.pocket.event;

import java.util.UUID;

/**
 * Se publica cuando se da de alta un dispositivo nuevo.
 *
 * Lleva el id y no la entidad: los consumidores corren después del commit, en
 * su propia transacción, y una entidad de la transacción anterior llegaría
 * desconectada del contexto de persistencia.
 */
public record UsuarioCreado(UUID usuarioId) {}
