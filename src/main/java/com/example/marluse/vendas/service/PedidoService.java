package com.example.marluse.vendas.service;

import com.example.marluse.clientes.model.Cliente;
import com.example.marluse.clientes.repository.ClienteRepository;
import com.example.marluse.entrega.enums.StatusEntrega;
import com.example.marluse.entrega.model.Entrega;
import com.example.marluse.estoque.model.Produto;
import com.example.marluse.estoque.repository.ProdutoRepository;
import com.example.marluse.financeiro.enums.StatusLancamento;
import com.example.marluse.financeiro.model.LancamentoFinanceiro;
import com.example.marluse.financeiro.repository.LancamentoFinanceiroRepository;
import com.example.marluse.financeiro.service.LancamentoFinanceiroService;
import com.example.marluse.vendas.dto.ItemPedidoRequest;
import com.example.marluse.vendas.dto.ItemPedidoResponse;
import com.example.marluse.vendas.dto.PedidoAtualizarRequest;
import com.example.marluse.vendas.dto.PedidoRequest;
import com.example.marluse.vendas.dto.PedidoResponse;
import com.example.marluse.vendas.enums.FormaPagamento;
import com.example.marluse.vendas.enums.StatusPedido;
import com.example.marluse.vendas.enums.TipoDesconto;
import com.example.marluse.vendas.model.ItemPedido;
import com.example.marluse.vendas.model.Pedido;
import com.example.marluse.vendas.repository.PedidoRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.example.marluse.financeiro.dto.ParcelaResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;

    private final LancamentoFinanceiroService lancamentoService;
    private final LancamentoFinanceiroRepository lancamentoRepository;
    @Transactional
    public PedidoResponse criar (PedidoRequest request) {

        Cliente cliente = null;
        if (request.clienteId() != null) {
            cliente = clienteRepository.findById(request.clienteId())
                    .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado"));
        }

        // Calcula forma de pagamento antes de definir o status inicial
        int numParcelas = request.numeroParcelas() != null && request.numeroParcelas() > 1
                ? request.numeroParcelas() : 1;
        boolean isPendente = request.formaPagamento() == FormaPagamento.FIADO || numParcelas > 1;

        // Fiado e parcelado exigem cliente cadastrado — não faz sentido registrar dívida sem devedor
        if (isPendente && cliente == null) {
            throw new IllegalArgumentException(
                    "Pagamento fiado ou parcelado exige um cliente cadastrado");
        }
        StatusLancamento statusLanc = isPendente ? StatusLancamento.PENDENTE : StatusLancamento.PAGO;

        StatusPedido statusInicial;
        if (request.status() != null) {
            statusInicial = request.status();
        } else if (!isPendente) {
            statusInicial = StatusPedido.PAGO;       // pagamento à vista → já pago
        } else {
            statusInicial = StatusPedido.CONFIRMADO; // parcelado / fiado → aguardando
        }

        Pedido pedido = Pedido.builder()
                .status(statusInicial)
                .formaPagamento(request.formaPagamento())
                .observacao(request.observacao())
                .dataVencimento(request.dataVencimento())
                .valorTotal(BigDecimal.ZERO)
                .dataMovimento(request.dataMovimento() != null ? request.dataMovimento() : LocalDate.now())
                .build();

        if (cliente != null) {
            pedido.setCliente(cliente);
        }

        BigDecimal total = BigDecimal.ZERO;


        for (ItemPedidoRequest itemRequest : request.itens()) {
            Produto produto = produtoRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));

            // Orçamento não baixa estoque
            if (statusInicial != StatusPedido.ORCAMENTO) {
                if (produto.getQuantidadeEstoque() < itemRequest.quantidade()) {
                    throw new IllegalArgumentException("Estoque insuficiente: " + produto.getNome());
                }

                produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - itemRequest.quantidade());
                produtoRepository.save(produto);
            }

            BigDecimal subTotal = produto.getPreco().multiply(BigDecimal.valueOf(itemRequest.quantidade()));

            ItemPedido item = ItemPedido.builder()
                    .pedido(pedido)
                    .produto(produto)
                    .quantidade(itemRequest.quantidade())
                    .custoUnitario(produto.getValorCompra())
                    .precoUnitario(produto.getPreco())
                    .subTotal(subTotal)
                    .build();

            pedido.getItens().add(item);
            total = total.add(subTotal);
        }

        pedido.setDesconto(request.desconto());
        pedido.setTipoDesconto(request.tipoDesconto());
        pedido.setJuros(request.juros());
        pedido.setTipoJuros(request.tipoJuros());
        if (request.entrega() != null) pedido.setEntrega(new Entrega(pedido, null, request.entrega().endereco(), request.entrega().dataPrevista(), null, StatusEntrega.PENDENTE));
        if (request.desconto() != null) pedido.setDescontoAplicadoEm(LocalDate.now());
        if (request.juros() != null) pedido.setJurosAplicadoEm(LocalDate.now());
        BigDecimal valorFinal = aplicarDesconto(total, request.desconto(), request.tipoDesconto());
        valorFinal = aplicarJuros(valorFinal, request.juros(), request.tipoJuros());
        pedido.setValorTotal(valorFinal);


        // Gera número sequencial antes de salvar
        long numeroPedido = pedidoRepository.count() + 1;
        pedido.setNumero(numeroPedido);

        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        // Orçamento não gera lançamento financeiro
        if (statusInicial != StatusPedido.ORCAMENTO) {
            String nomeCliente = cliente != null ? cliente.getNome() : "Consumidor Final";
            String descricao = String.format("Pedido #%03d - %s", numeroPedido, nomeCliente);

            LocalDate vencimento = request.primeiroVencimento() != null ? request.primeiroVencimento()
                    : request.dataVencimento() != null ? request.dataVencimento()
                    : isPendente ? LocalDate.now().plusDays(30) : LocalDate.now();

            lancamentoService.registrarVendaReceita(pedidoSalvo, descricao, valorFinal, statusLanc, vencimento, numParcelas);
        }

        // Popula a próxima parcela pendente na resposta para reatividade do frontend
        ParcelaResponse parcelaMesAtual = null;
        if (numParcelas > 1) {
            parcelaMesAtual = lancamentoRepository.findByPedidoId(pedidoSalvo.getId()).stream()
                    .filter(l -> l.getStatus() == StatusLancamento.PENDENTE)
                    .min(Comparator.comparing(LancamentoFinanceiro::getDataVencimento,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(ParcelaResponse::from)
                    .orElse(null);
        }
        return PedidoResponse.from(pedidoSalvo, null, parcelaMesAtual);
    }

    public List<PedidoResponse> listar() {
        // Próxima parcela PENDENTE de cada pedido parcelado (ainda em aberto)
        Map<String, ParcelaResponse> proximaPendentePorPedido =
                lancamentoRepository.findProximasParcelasPendentes().stream()
                        .filter(l -> l.getPedido() != null)
                        .collect(Collectors.toMap(
                                l -> l.getPedido().getId(),
                                ParcelaResponse::from,
                                (a, b) -> a   // ordenado ASC → mantém a mais próxima
                        ));

        // Última parcela PAGO de pedidos parcelados totalmente quitados
        Map<String, ParcelaResponse> ultimaPagaPorPedido =
                lancamentoRepository.findUltimasParcelasDePedidosPagos().stream()
                        .filter(l -> l.getPedido() != null)
                        .collect(Collectors.toMap(
                                l -> l.getPedido().getId(),
                                ParcelaResponse::from,
                                (a, b) -> a   // ordenado DESC → mantém a última (maior numParcelas)
                        ));

        // Mescla: PENDENTE tem prioridade sobre PAGO
        Map<String, ParcelaResponse> parcelaPorPedido = new HashMap<>(ultimaPagaPorPedido);
        parcelaPorPedido.putAll(proximaPendentePorPedido);

        return pedidoRepository.findAll().stream()
                .map(p -> PedidoResponse.from(p, null, parcelaPorPedido.get(p.getId())))
                .toList();
    }

    public PedidoResponse buscarPorId(String id){
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
        List<ParcelaResponse> parcelas = listarParcelas(id);
        return PedidoResponse.from(pedido, parcelas);
    }



    public List<PedidoResponse> listarPorStatus(StatusPedido status) {
        return pedidoRepository.findByStatus(status)
                .stream()
                .map(PedidoResponse::from)
                .toList();
    }

    @Transactional
    public PedidoResponse pagar(String id) {
        Pedido pedido = buscarEntidade(id);

        if (pedido.getStatus() == StatusPedido.PAGO) {
            throw new IllegalArgumentException("Pedido já está pago");
        }

        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new IllegalArgumentException("Pedido cancelado não pode ser pago");
        }

        // Paga lancamentos ainda pendentes (ex.: FIADO, ou ação direta de pagar pedido)
        lancamentoRepository.findByPedidoId(id).stream()
                .filter(l -> l.getStatus() != StatusLancamento.PAGO)
                .forEach(l -> lancamentoService.pagar(l.getId()));

        pedido.setStatus(StatusPedido.PAGO);
        pedidoRepository.save(pedido);

        // Retorna última parcela PAGO para pedidos parcelados (para exibir "N/N pagas" no frontend)
        List<LancamentoFinanceiro> lancamentos = lancamentoRepository.findByPedidoId(id);
        boolean ehParcelado = lancamentos.stream()
                .anyMatch(l -> l.getTotalParcelas() != null && l.getTotalParcelas() > 1);
        ParcelaResponse parcelaMesAtual = null;
        if (ehParcelado) {
            parcelaMesAtual = lancamentos.stream()
                    .filter(l -> l.getStatus() == StatusLancamento.PAGO)
                    .max(Comparator.comparing(LancamentoFinanceiro::getNumParcelas,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .map(ParcelaResponse::from)
                    .orElse(null);
        }
        return PedidoResponse.from(pedido, null, parcelaMesAtual);
    }

    @Transactional
    public PedidoResponse cancelar(String id) {
        Pedido pedido = buscarEntidade(id);

        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new IllegalArgumentException("Pedido já está cancelado");
        }


        // Orçamento nunca reservou estoque, então não restaura
        if (pedido.getStatus() != StatusPedido.ORCAMENTO) {
            for (ItemPedido item : pedido.getItens()) {
                Produto produto = item.getProduto();
                produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() + item.getQuantidade());
                produtoRepository.save(produto);
            }
        }

        lancamentoRepository.findByPedidoId(id).forEach(l -> {
            l.setStatus(StatusLancamento.CANCELADO);
            lancamentoRepository.save(l);
        });

        pedido.setStatus(StatusPedido.CANCELADO);
        return toResponse(pedidoRepository.save(pedido));
    }

    private PedidoResponse toResponse(Pedido pedido) {
        return PedidoResponse.from(pedido);
    }

    @Transactional
    public PedidoResponse atualizar(String id, PedidoAtualizarRequest request) {
        Pedido pedido = buscarEntidade(id);
        if (request.formaPagamento() != null) pedido.setFormaPagamento(request.formaPagamento());
        if (request.observacao() != null) pedido.setObservacao(request.observacao());
        if (request.dataMovimento() != null) pedido.setDataMovimento(request.dataMovimento());

        if (request.desconto() != null ) {
            pedido.setDesconto(request.desconto());
            pedido.setTipoDesconto(request.tipoDesconto());
            pedido.setDescontoAplicadoEm(LocalDate.now());
            // Aplica desconto sobre o valorTotal atual (cumulativo)
            pedido.setValorTotal(aplicarDesconto(pedido.getValorTotal(), request.desconto(), request.tipoDesconto()));
        }

        if (request.juros() != null) {
            pedido.setJuros(request.juros());
            pedido.setTipoJuros(request.tipoJuros());
            pedido.setJurosAplicadoEm(LocalDate.now());
            pedido.setValorTotal(aplicarJuros(pedido.getValorTotal(), request.juros(), request.tipoJuros()));
        }

        List<LancamentoFinanceiro> pendentes = lancamentoRepository
                .findByPedidoIdAndStatusNot(id, StatusLancamento.PAGO);
        if (!pendentes.isEmpty()) {
            BigDecimal pago = lancamentoRepository.findByPedidoId(id).stream()
                    .filter(l -> l.getStatus() == StatusLancamento.PAGO)
                    .map(LancamentoFinanceiro::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal restante = pedido.getValorTotal().subtract(pago);
            if (restante.compareTo(BigDecimal.ZERO) < 0)
                throw new IllegalArgumentException("Desconto resulta em valor menor que o já pago");
            BigDecimal valorParcela = restante.divide(
                    BigDecimal.valueOf(pendentes.size()), 2, RoundingMode.HALF_UP);
            pendentes.forEach(l -> {
                l.setValor(valorParcela);
                lancamentoRepository.save(l);
            });
        }
        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        // Retorna a próxima parcela pendente para manter o badge na tabela
        ParcelaResponse parcelaMesAtual = lancamentoRepository.findByPedidoId(id).stream()
                .filter(l -> l.getStatus() == StatusLancamento.PENDENTE)
                .min(Comparator.comparing(LancamentoFinanceiro::getDataVencimento,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ParcelaResponse::from)
                .orElse(null);

        return PedidoResponse.from(pedidoSalvo, null, parcelaMesAtual);
    }

    public List<ParcelaResponse> listarParcelas(String pedidoId) {
        return lancamentoRepository.findByPedidoId(pedidoId).stream()
                .sorted(Comparator.comparing(LancamentoFinanceiro::getDataVencimento,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ParcelaResponse::from)
                .toList();
    }

    public BigDecimal somarVendasPorPeriodo(LocalDate inicio, LocalDate fim){
        // Usa dataPagamento dos lançamentos para refletir quando o dinheiro entrou,
        // não a data de criação do pedido (correto para pedidos parcelados)
        return lancamentoRepository.somarReceitaVendasPorPagamento(inicio, fim);
    }

    private BigDecimal aplicarDesconto(BigDecimal bruto, BigDecimal desconto, TipoDesconto tipo) {
        if (desconto == null || desconto.compareTo(BigDecimal.ZERO) == 0) return bruto;

        BigDecimal calc = tipo == TipoDesconto.PERCENTUAL
                ? bruto.multiply(desconto).divide(BigDecimal.valueOf(100))
                : desconto;

        BigDecimal resultado = bruto.subtract(calc);

        if (resultado.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Desconto não pode ser maior que o valor total");
        return resultado;
    }

    private BigDecimal aplicarJuros(BigDecimal base, BigDecimal juros, TipoDesconto tipo) {
        if (juros == null || juros.compareTo(BigDecimal.ZERO) == 0) return base;

        BigDecimal calc = tipo == TipoDesconto.PERCENTUAL
                ? base.multiply(juros).divide(BigDecimal.valueOf(100))
                : juros;

        return base.add(calc);
    }


    @Transactional
    public PedidoResponse confirmar(String id) {
        Pedido pedido = buscarEntidade(id);

        if (pedido.getStatus() != StatusPedido.ORCAMENTO) {
            throw new IllegalArgumentException("Apenas orçamentos podem ser confirmados");
        }

        // Baixa estoque e faz snapshot do custo no item
        for (ItemPedido item : pedido.getItens()) {
            Produto produto = item.getProduto();
            if (produto.getQuantidadeEstoque() < item.getQuantidade()) {
                throw new IllegalArgumentException("Estoque insuficiente para: " + produto.getNome());
            }
            produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - item.getQuantidade());
            produtoRepository.save(produto);

            // Registra o custo de compra vigente no momento da confirmação
            item.setCustoUnitario(produto.getValorCompra());
        }

        // Gera lançamento financeiro
        int numParcelas = 1;
        boolean isPendente = pedido.getFormaPagamento() == FormaPagamento.FIADO;
        StatusLancamento statusLanc = isPendente ? StatusLancamento.PENDENTE : StatusLancamento.PAGO;

        String nomeCliente = pedido.getCliente() != null ? pedido.getCliente().getNome() : "Consumidor Final";
        String descricao = String.format("Pedido #%03d - %s", pedido.getNumero(), nomeCliente);
        LocalDate vencimento = pedido.getDataVencimento() != null ? pedido.getDataVencimento() : LocalDate.now();

        lancamentoService.registrarVendaReceita(pedido, descricao, pedido.getValorTotal(), statusLanc, vencimento, numParcelas);

        pedido.setStatus(isPendente ? StatusPedido.CONFIRMADO : StatusPedido.PAGO);
        return toResponse(pedidoRepository.save(pedido));
    }

    @Transactional
    public void excluir(String id) {
        Pedido pedido = buscarEntidade(id);
        lancamentoRepository.deleteAll(lancamentoRepository.findByPedidoId(id));
        pedidoRepository.delete(pedido);
    }

    private Pedido buscarEntidade(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado: " + id));
    }
}
