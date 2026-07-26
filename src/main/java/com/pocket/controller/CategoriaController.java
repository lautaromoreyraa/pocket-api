package com.pocket.controller;

import com.pocket.dto.categoria.CategoriaResponse;
import com.pocket.mapper.categoria.CategoriaMapper;
import com.pocket.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;
    private final CategoriaMapper categoriaMapper;

    @GetMapping
    public ResponseEntity<List<CategoriaResponse>> listar() {
        return ResponseEntity.ok(
                categoriaMapper.aResponse(categoriaRepository.findAllByOrderByOrdenAsc()));
    }
}
