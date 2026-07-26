package com.pocket.mapper.categoria.impl;

import com.pocket.domain.Categoria;
import com.pocket.dto.categoria.CategoriaResponse;
import com.pocket.mapper.categoria.CategoriaMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CategoriaMapperImpl implements CategoriaMapper {

    @Override
    public CategoriaResponse aResponse(Categoria c) {
        if (c == null) return null;
        return new CategoriaResponse(c.getId(), c.getNombre(), c.getIcono(), c.getColor(), c.getOrden());
    }

    @Override
    public List<CategoriaResponse> aResponse(List<Categoria> categorias) {
        return categorias == null ? List.of() : categorias.stream().map(this::aResponse).toList();
    }
}
