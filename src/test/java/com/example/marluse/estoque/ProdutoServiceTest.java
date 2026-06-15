package com.example.marluse.estoque;

import com.example.marluse.estoque.dto.ProdutoAtualizarRequest;
import com.example.marluse.estoque.dto.ProdutoRequest;
import com.example.marluse.estoque.dto.ProdutoResponse;
import com.example.marluse.estoque.enums.UnidadeMedida;
import com.example.marluse.estoque.repository.ProdutoRepository;
import com.example.marluse.estoque.service.ProdutoService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ProdutoServiceTest {

    @Autowired
    private ProdutoService produtoService;

    @Autowired
    private ProdutoRepository produtoRepository;

    @BeforeEach
    void setUp(){
        produtoRepository.deleteAll();
    }

    private ProdutoRequest produtoValido(String nome, int quantidade) {
        return new ProdutoRequest(nome, "Descrição", new BigDecimal("25.00"), quantidade, 5, UnidadeMedida.SACO);
    }

    @Test
    void deveCriarProdutoComSucesso() {
        ProdutoResponse response = produtoService.criar(produtoValido("Cimento", 50));

        assertNotNull(response.id());
        assertEquals("Cimento", response.nome());
        assertEquals(50, response.quantidadeEstoque());
        assertTrue(response.ativo());
    }

    @Test
    void deveListarApenasAtivos() {
        ProdutoResponse p1 = produtoService.criar(produtoValido("Cimento", 50));
        ProdutoResponse p2 = produtoService.criar(produtoValido("Areia", 30));
        produtoService.inativar(p2.id());

        List<ProdutoResponse> ativos = produtoService.listar();

        assertEquals(1, ativos.size());
        assertEquals("Cimento", ativos.get(0).nome());
    }

    @Test
    void deveIdentificarEstoqueBaixo() {
        produtoService.criar(produtoValido("Cimento", 3)); // abaixo do mínimo (5)
        produtoService.criar(produtoValido("Areia", 20)); // acima do mínimo

        List<ProdutoResponse> resultado = produtoService.listarEstoqueBaixo();

        assertEquals(1, resultado.size());
        assertTrue(resultado.get(0).estoqueBaixo());
    }

    @Test
    void deveAtualizarProduto() {
        ProdutoResponse criado = produtoService.criar(produtoValido("Cimento", 50));

        ProdutoAtualizarRequest atualizado = new ProdutoAtualizarRequest("Cimento CP-II", "Nova descrição", new BigDecimal("30.00"), 50, 10, UnidadeMedida.SACO);
        ProdutoResponse response = produtoService.atualizar(criado.id(), atualizado);

        assertEquals("Cimento CP-II", response.nome());
        assertEquals(new BigDecimal("30.00"), response.preco());
    }

    @Test
    void deveLancarExcecaoAoBuscarProdutoInexistente() {
        assertThrows(EntityNotFoundException.class, () -> produtoService.burcarPorId("id-inexistente"));
    }

    @Test
    void deveInativarProduto() {
        ProdutoResponse criado = produtoService.criar(produtoValido("Cimento", 50));

        produtoService.inativar(criado.id());

        ProdutoResponse inativado = produtoService.burcarPorId(criado.id());
        assertFalse(inativado.ativo());
    }


}
