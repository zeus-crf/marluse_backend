package com.example.marluse.locacoes;

import com.example.marluse.clientes.model.Cliente;
import com.example.marluse.clientes.repository.ClienteRepository;
import com.example.marluse.estoque.enums.UnidadeMedida;
import com.example.marluse.estoque.model.Produto;
import com.example.marluse.estoque.repository.ProdutoRepository;
import com.example.marluse.locacoes.dto.ItemLocacaoRequest;
import com.example.marluse.locacoes.dto.LocacaoRequest;
import com.example.marluse.locacoes.dto.LocacaoResponse;
import com.example.marluse.locacoes.enums.StatusLocacao;
import com.example.marluse.locacoes.model.Locacao;
import com.example.marluse.locacoes.repository.LocacaoRepository;
import com.example.marluse.locacoes.service.LocacaoService;
import com.example.marluse.vendas.enums.FormaPagamento;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class LocacoesServiceTest {

    @Autowired
    private  LocacaoRepository locacaoRepository;

    @Autowired
    private LocacaoService locacaoService;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    private Produto produto;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        clienteRepository.deleteAll();
        produtoRepository.deleteAll();
        locacaoRepository.deleteAll();

        produto = produtoRepository.save(Produto.builder()
                .nome("Betoneira")
                .preco(new BigDecimal("50.00"))
                .quantidadeEstoque(5)
                .estoqueMinimo(1)
                .medida(UnidadeMedida.PECA)
                .ativo(true)
                .build());

        cliente = clienteRepository.save(Cliente.builder()
                .nome("Maria Santos")
                .cpfCnpj("987.654.321-00")
                .ativo(true)
                .consumidorFinal(false)
                .build());
    }

        private LocacaoRequest locacaoValida (String clienteId, int quantidade, int dias) {
            LocalDate retirada = LocalDate.now();
            LocalDate devolucao = retirada.plusDays(dias);
            return new LocacaoRequest(
                    clienteId,
                    FormaPagamento.PIX,
                    retirada,
                    devolucao,
                    List.of(new ItemLocacaoRequest(produto.getId(), quantidade)),
                    null
            );
    }

    @Test
    void deveCriarLocacaoComSucesso(){
        LocacaoResponse response = locacaoService.criar(locacaoValida(cliente.getId(), 2, 3));

        assertNotNull(response.id());
        assertEquals(StatusLocacao.ATIVA, response.status());
        assertEquals(new BigDecimal("300.00"), response.valorTotal());
        assertEquals(1, response.itens().size());

    }

    @Test
    void deveDiminuirEstoqueAoCriarLocacao() {
        locacaoService.criar(locacaoValida(null, 2, 5));

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(3, atualizado.getQuantidadeEstoque());
    }

    @Test
    void deveLancarExcecaoQuandoEstoqueInsuficiente() {
        assertThrows(IllegalArgumentException.class,
                () -> locacaoService.criar(locacaoValida(null, 10, 3)));
    }

    @Test
    void deveLancarExcecaoQuandoDatasInvalidas() {
        LocalDate retirada = LocalDate.now();
        LocacaoRequest request = new LocacaoRequest(
                null,
                FormaPagamento.DINHEIRO,
                retirada,
                retirada, // mesma data — inválido
                List.of(new ItemLocacaoRequest(produto.getId(), 1)),
                null
        );

        assertThrows(IllegalArgumentException.class, () -> locacaoService.criar(request));
    }

    @Test
    void deveDevolverLocacaoERestaurarEstoque() {
        LocacaoResponse locacao = locacaoService.criar(locacaoValida(null, 3, 5));

        locacaoService.devolver(locacao.id());

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(5, atualizado.getQuantidadeEstoque());
    }

    @Test
    void deveCriarLocacaoSemCliente() {
        LocacaoResponse response = locacaoService.criar(locacaoValida(null, 1, 2));

        assertNull(response.clienteId());
        assertEquals("Consumidor Final", response.clienteNome());
    }
}
