package com.example.marluse.locacoes;

import com.example.marluse.clientes.model.Cliente;
import com.example.marluse.clientes.repository.ClienteRepository;
import com.example.marluse.estoque.enums.TipoProduto;
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
import com.example.marluse.locacoes.enums.UnidadeCobranca;
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
                .valorCompra(new BigDecimal("30.00"))
                .quantidadeEstoque(BigDecimal.valueOf(5))
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

    /** Item com produto existente, baixando estoque. */
    private ItemLocacaoRequest item(String produtoId, int quantidade) {
        return new ItemLocacaoRequest(produtoId, null, BigDecimal.valueOf(quantidade), null, UnidadeCobranca.DIARIA, true, false);
    }

    /** LocacaoRequest mínimo: campos essenciais, o resto null. */
    private LocacaoRequest locacaoRequest(String clienteId, FormaPagamento forma,
                                          LocalDate retirada, LocalDate devolucao,
                                          List<ItemLocacaoRequest> itens, String observacao) {
        return new LocacaoRequest(
                clienteId, forma, retirada, devolucao, itens, observacao,
                null, null, null, null, null, null, null, null, null);
    }

    private LocacaoRequest locacaoValida(String clienteId, int quantidade, int dias) {
        LocalDate retirada = LocalDate.now();
        LocalDate devolucao = retirada.plusDays(dias);
        return locacaoRequest(clienteId, FormaPagamento.PIX, retirada, devolucao,
                List.of(item(produto.getId(), quantidade)), null);
    }

    @Test
    void deveCriarLancamentoAoCriarLocacao() {
        LocacaoRequest request = locacaoRequest(
                cliente.getId(), FormaPagamento.PIX,
                LocalDate.now(), LocalDate.now().plusDays(3),
                List.of(item(produto.getId(), 1)), null);

        LocacaoResponse locacao = locacaoService.criar(request, false);

        List<LancamentoFinanceiro> lancamentos = lancamentoRepository.findAll();
        assertEquals(1, lancamentos.size());

        LancamentoFinanceiro lancamento = lancamentos.get(0);
        assertEquals(TipoLancamento.RECEITA, lancamento.getTipo());
        // Locação nasce PENDENTE — pagamento é confirmado na devolução
        assertEquals(StatusLancamento.PENDENTE, lancamento.getStatus());
        assertEquals(locacao.id(), lancamento.getLocacao().getId());
    }

    @Test
    void deveCriarLancamentoPendenteAoCriarLocacaoFiado() {
        LocalDate dataFim = LocalDate.now().plusDays(5);

        LocacaoRequest request = locacaoRequest(
                cliente.getId(), FormaPagamento.FIADO,
                LocalDate.now(), dataFim,
                List.of(item(produto.getId(), 1)), null);
        locacaoService.criar(request, false);

        List<LancamentoFinanceiro> lancamentos = lancamentoRepository.findAll();
        assertEquals(1, lancamentos.size());

        LancamentoFinanceiro lancamento = lancamentos.get(0);
        assertEquals(StatusLancamento.PENDENTE, lancamento.getStatus());
        assertEquals(dataFim.plusDays(1), lancamento.getDataVencimento());
        assertNull(lancamento.getDataPagamento());
    }

    @Test
    void deveCriarLocacaoComSucesso(){
        LocacaoResponse response = locacaoService.criar(locacaoValida(cliente.getId(), 2, 3), false);

        assertNotNull(response.id());
        assertEquals(StatusLocacao.ATIVA, response.status());
        assertEquals(new BigDecimal("300.00"), response.valorTotal());
        assertEquals(1, response.itens().size());

    }

    @Test
    void deveDiminuirEstoqueAoCriarLocacao() {
        locacaoService.criar(locacaoValida(null, 2, 5), false);

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, atualizado.getQuantidadeEstoque().compareTo(BigDecimal.valueOf(3)));
    }

    @Test
    void deveLancarExcecaoQuandoEstoqueInsuficiente() {
        assertThrows(IllegalArgumentException.class,
                () -> locacaoService.criar(locacaoValida(null, 10, 3), false));
    }

    @Test
    void deveLancarExcecaoQuandoDatasInvalidas() {
        LocalDate retirada = LocalDate.now();
        LocacaoRequest request = locacaoRequest(
                null, FormaPagamento.DINHEIRO,
                retirada, retirada, // mesma data — inválido
                List.of(item(produto.getId(), 1)), null);

        assertThrows(IllegalArgumentException.class, () -> locacaoService.criar(request, false));
    }

    @Test
    void deveDevolverLocacaoERestaurarEstoque() {
        LocacaoResponse locacao = locacaoService.criar(locacaoValida(null, 3, 5), false);

        locacaoService.devolver(locacao.id());

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(0, atualizado.getQuantidadeEstoque().compareTo(BigDecimal.valueOf(5)));
    }

    @Test
    void deveCriarLocacaoSemCliente() {
        LocacaoResponse response = locacaoService.criar(locacaoValida(null, 1, 2), false);

        assertNull(response.clienteId());
        assertEquals("Consumidor Final", response.clienteNome());
    }

    /** Cria uma locação com um produto de locação (diária 20, semanal 100, mensal 300),
     *  um item qtd 1 usando a unidade, o preço unitário e o nº de dias informados,
     *  sem baixar estoque, à vista. */
    private LocacaoResponse criarLocacaoComUnidade(UnidadeCobranca unidade, BigDecimal precoUnitario, int dias) {
        Produto locacao = produtoRepository.save(Produto.builder()
                .nome("Betoneira")
                .tipo(TipoProduto.LOCACAO)
                .preco(BigDecimal.ZERO)
                .precoDiaria(new BigDecimal("20.00"))
                .precoSemanal(new BigDecimal("100.00"))
                .precoMensal(new BigDecimal("300.00"))
                .valorCompra(new BigDecimal("100.00"))
                .quantidadeEstoque(BigDecimal.valueOf(5))
                .estoqueMinimo(1)
                .medida(UnidadeMedida.PECA)
                .ativo(true)
                .build());

        ItemLocacaoRequest item = new ItemLocacaoRequest(
                locacao.getId(), null, BigDecimal.ONE, precoUnitario, unidade, false, false);

        LocacaoRequest request = locacaoRequest(
                null, FormaPagamento.DINHEIRO,
                LocalDate.now(), LocalDate.now().plusDays(dias),
                List.of(item), null);

        return locacaoService.criar(request, false);
    }

    @Test
    void deveCobrarSemanaCheiaMaisDiasAvulsos() {
        // diária 20, semanal 100; 10 dias => 1 semana (100) + 3 dias (3×20=60) = 160
        LocacaoResponse r = criarLocacaoComUnidade(UnidadeCobranca.SEMANAL, new BigDecimal("100.00"), 10);
        assertEquals(0, new BigDecimal("160.00").compareTo(r.itens().get(0).subtotal()));
        assertEquals(UnidadeCobranca.SEMANAL, r.itens().get(0).unidadeCobranca());
    }

    @Test
    void deveCobrarMesCheioMaisDiaAvulso() {
        // diária 20, mensal 300; 31 dias => 1 mês (300) + 1 dia (20) = 320 (não 2 meses)
        LocacaoResponse r = criarLocacaoComUnidade(UnidadeCobranca.MENSAL, new BigDecimal("300.00"), 31);
        assertEquals(0, new BigDecimal("320.00").compareTo(r.itens().get(0).subtotal()));
        assertEquals(UnidadeCobranca.MENSAL, r.itens().get(0).unidadeCobranca());
    }

    @Test
    void deveDerivarSemanalDaDiariaQuandoNaoCadastrada() {
        // Produto de locação só com diária (20), SEM semanal/mensal — cenário de produto existente.
        Produto soDiaria = produtoRepository.save(Produto.builder()
                .nome("Compactador")
                .tipo(TipoProduto.LOCACAO)
                .preco(BigDecimal.ZERO)
                .precoDiaria(new BigDecimal("20.00"))
                .valorCompra(new BigDecimal("100.00"))
                .quantidadeEstoque(BigDecimal.valueOf(5))
                .estoqueMinimo(1)
                .medida(UnidadeMedida.PECA)
                .ativo(true)
                .build());

        // Item sem preço explícito (precoDiaria null) => o backend deriva a tarifa pela unidade.
        ItemLocacaoRequest item = new ItemLocacaoRequest(
                soDiaria.getId(), null, BigDecimal.ONE, null, UnidadeCobranca.SEMANAL, false, false);
        LocacaoRequest request = locacaoRequest(
                null, FormaPagamento.DINHEIRO,
                LocalDate.now(), LocalDate.now().plusDays(10),
                List.of(item), null);

        LocacaoResponse r = locacaoService.criar(request, false);

        // Semanal derivada = 20 × 7 = 140; 10 dias => 1 semana (140) + 3 dias (3×20=60) = 200
        // (com tarifas derivadas, o híbrido equivale à diária pura: 10 × 20 = 200)
        assertEquals(0, new BigDecimal("200.00").compareTo(r.itens().get(0).subtotal()));
    }
}
