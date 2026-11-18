package com.pocket.service.categoria.impl;

import com.pocket.dto.categoria.CategoriaResponse;
import com.pocket.mapper.categoria.CategoriaMapper;
import com.pocket.repository.CategoriaRepository;
import com.pocket.service.categoria.CategoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaServiceImpl implements CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final CategoriaMapper categoriaMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar() {
        return categoriaMapper.aResponse(categoriaRepository.findAllByOrderByOrdenAsc());
    }
}
