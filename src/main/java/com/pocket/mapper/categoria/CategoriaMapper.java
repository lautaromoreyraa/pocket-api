package com.pocket.mapper.categoria;

import com.pocket.domain.Categoria;
import com.pocket.dto.categoria.CategoriaResponse;

import java.util.List;

public interface CategoriaMapper {
    CategoriaResponse aResponse(Categoria categoria);
    List<CategoriaResponse> aResponse(List<Categoria> categorias);
}
