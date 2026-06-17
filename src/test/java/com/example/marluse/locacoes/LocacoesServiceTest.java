package com.example.marluse.locacoes;

import com.example.marluse.clientes.model.Cliente;
import com.example.marluse.clientes.repository.ClienteRepository;
import com.example.marluse.estoque.enums.UnidadeMedida;
import com.example.marluse.estoque.model.Produto;
import com.example.marluse.estoque.repository.ProdutoRepository;
import com.example.marluse.financeiro.enums.StatusLancamento;
import com.example.marluse.financeiro.enums.TipoLancamento;
import com.example.marluse.financeiro.model.LancamentoFinanceiro;
import com.example.marluse.financeiro.repository.LancamentoFinanceiroRepository;
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

    @Autowired
    private LancamentoFinanceiroRepository lancamentoRepository;

    private Produto produto;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        lancamentoRepository.deleteAll();
        locacaoRepository.deleteAll();
        produtoRepository.deleteAll();
        clienteRepository.deleteAll();

        produto = produtoRepository.save(Produto.builder()
                .nome("Andaime")
                .preco(new BigDecimal("50.00"))
                .quantidadeEstoque(5)
                .estoqueMinimo(2)
                .medida(UnidadeMedida.PECA)
                .ativo(true)
                .build());

        cliente = clienteRepository.save(Cliente.builder()
                .nome("Maria Costa")
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
    void deveCriarLancamentoPagoAoCriarLocacaoComPagamentoImediato() {
        LocacaoRequest request = new LocacaoRequest(
                cliente.getId(),
                FormaPagamento.PIX,
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                List.of(new ItemLocacaoRequest(produto.getId(), 1)),
                null
        );

        LocacaoResponse locacao = locacaoService.criar(request);

        List<LancamentoFinanceiro> lancamentos = lancamentoRepository.findAll();
        assertEquals(1, lancamentos.size());

        LancamentoFinanceiro lancamento = lancamentos.get(0);
        assertEquals(TipoLancamento.RECEITA, lancamento.getTipo());
        assertEquals(StatusLancamento.PAGO, lancamento.getStatus());
        assertEquals("Locação", lancamento.getCategoria());
        assertEquals(locacao.id(), lancamento.getLocacao().getId());
        assertNotNull(lancamento.getDataPagamento());
    }

    @Test
    void deveCriarLancamentoPendenteAoCriarLocacaoFiado() {
        LocalDate dataFim = LocalDate.now().plusDays(5);

        LocacaoRequest request = new LocacaoRequest(
                cliente.getId(),
                FormaPagamento.FIADO,
                LocalDate.now(),
                dataFim,
                List.of(new ItemLocacaoRequest(produto.getId(), 1)),
                null
        );
        locacaoService.criar(request);

        List<LancamentoFinanceiro> lancamentos = lancamentoRepository.findAll();
        assertEquals(1, lancamentos.size());

        LancamentoFinanceiro lancamento = lancamentos.get(0);
        assertEquals(StatusLancamento.PENDENTE, lancamento.getStatus());
        assertEquals(dataFim.plusDays(1), lancamento.getDataVencimento());
        assertNull(lancamento.getDataPagamento());
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
