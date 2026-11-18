package com.pocket.service.categoria;

import com.pocket.domain.Categoria;
import com.pocket.dto.categoria.CategoriaResponse;
import com.pocket.mapper.categoria.CategoriaMapper;
import com.pocket.repository.CategoriaRepository;
import com.pocket.service.categoria.impl.CategoriaServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceImplTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private CategoriaMapper categoriaMapper;

    @InjectMocks
    private CategoriaServiceImpl categoriaService;

    @Test
    void listarDevuelveLasCategoriasEnElOrdenDelRepositorio() {
        List<Categoria> categorias = List.of(mock(Categoria.class), mock(Categoria.class));
        List<CategoriaResponse> respuestas = List.of(mock(CategoriaResponse.class), mock(CategoriaResponse.class));
        when(categoriaRepository.findAllByOrderByOrdenAsc()).thenReturn(categorias);
        when(categoriaMapper.aResponse(categorias)).thenReturn(respuestas);

        assertThat(categoriaService.listar()).isSameAs(respuestas);
    }
}
