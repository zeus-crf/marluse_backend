package com.example.marluse.estoque;

import com.example.marluse.estoque.dto.CategoriaProduto;
import com.example.marluse.estoque.dto.ProdutoAtualizarRequest;
import com.example.marluse.estoque.dto.ProdutoRequest;
import com.example.marluse.estoque.dto.ProdutoResponse;
import com.example.marluse.estoque.enums.UnidadeMedida;
import com.example.marluse.estoque.repository.ProdutoRepository;
import com.example.marluse.estoque.service.ProdutoService;
import com.example.marluse.fornecedores.repository.FornecedorRepository;
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

    @Autowired
    private FornecedorRepository fornecedorRepository;

    @BeforeEach
    void setUp(){
        produtoRepository.deleteAll();
        fornecedorRepository.deleteAll();
    }

    private ProdutoRequest produtoValido(String nome, int quantidade) {
        return produtoValido(nome, quantidade, List.of());
    }

    private ProdutoRequest produtoValido(String nome, int quantidade, List<String> fornecedores) {
        return new ProdutoRequest(
                nome, "Descrição",
                new BigDecimal("10.00"),   // valorCompra
                new BigDecimal("25.00"),   // preco
                new BigDecimal("5.00"),    // precoDiaria
                BigDecimal.valueOf(quantidade), 5,
                UnidadeMedida.SACO,
                CategoriaProduto.OUTROS,
                fornecedores);
    }

    @Test
    void deveCriarProdutoComSucesso() {
        ProdutoResponse response = produtoService.criar(produtoValido("Cimento", 50));

        assertNotNull(response.id());
        assertEquals("Cimento", response.nome());
        assertEquals(0, response.quantidadeEstoque().compareTo(BigDecimal.valueOf(50)));
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

        ProdutoAtualizarRequest atualizado = new ProdutoAtualizarRequest(
                "Cimento CP-II", "Nova descrição",
                new BigDecimal("12.00"),   // valorCompra
                new BigDecimal("30.00"),   // preco
                new BigDecimal("6.00"),    // precoDiaria
                BigDecimal.valueOf(50), 10,
                UnidadeMedida.SACO,
                CategoriaProduto.OUTROS,
                null);   // null = não mexe nos fornecedores
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

    // --- fornecedores -------------------------------------------------------

    @Test
    void deveCriarProdutoComFornecedoresNovos() {
        ProdutoResponse response =
                produtoService.criar(produtoValido("Cimento", 50, List.of("Votorantim", "Tigre")));

        assertEquals(List.of("Tigre", "Votorantim"), response.fornecedores());
        assertEquals(2, fornecedorRepository.count());
    }

    @Test
    void deveReaproveitarFornecedorEntreProdutos() {
        produtoService.criar(produtoValido("Cimento", 50, List.of("Votorantim")));
        produtoService.criar(produtoValido("Areia", 30, List.of("votorantim")));

        assertEquals(1, fornecedorRepository.count(), "mesmo fornecedor não deve ser duplicado");
    }

    @Test
    void deveCriarProdutoSemFornecedores() {
        ProdutoResponse response = produtoService.criar(produtoValido("Cimento", 50));

        assertTrue(response.fornecedores().isEmpty());
    }

    @Test
    void deveSubstituirOsFornecedoresNoUpdate() {
        ProdutoResponse criado =
                produtoService.criar(produtoValido("Cimento", 50, List.of("Votorantim")));

        ProdutoResponse atualizado = produtoService.atualizar(criado.id(),
                atualizarComFornecedores(List.of("Tigre")));

        assertEquals(List.of("Tigre"), atualizado.fornecedores());
    }

    @Test
    void deveLimparOsFornecedoresComListaVazia() {
        ProdutoResponse criado =
                produtoService.criar(produtoValido("Cimento", 50, List.of("Votorantim")));

        ProdutoResponse atualizado = produtoService.atualizar(criado.id(),
                atualizarComFornecedores(List.of()));

        assertTrue(atualizado.fornecedores().isEmpty());
    }

    @Test
    void devePreservarOsFornecedoresQuandoOCampoVemNulo() {
        ProdutoResponse criado =
                produtoService.criar(produtoValido("Cimento", 50, List.of("Votorantim")));

        ProdutoResponse atualizado = produtoService.atualizar(criado.id(),
                atualizarComFornecedores(null));

        assertEquals(List.of("Votorantim"), atualizado.fornecedores());
    }

    private ProdutoAtualizarRequest atualizarComFornecedores(List<String> fornecedores) {
        return new ProdutoAtualizarRequest(
                "Cimento", "Descrição",
                new BigDecimal("10.00"),
                new BigDecimal("25.00"),
                new BigDecimal("5.00"),
                BigDecimal.valueOf(50), 5,
                UnidadeMedida.SACO,
                CategoriaProduto.OUTROS,
                fornecedores);
    }
}
