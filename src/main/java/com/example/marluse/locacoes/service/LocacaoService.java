package com.example.marluse.locacoes.service;

import com.example.marluse.clientes.model.Cliente;
import com.example.marluse.clientes.repository.ClienteRepository;
import com.example.marluse.entrega.enums.StatusEntrega;
import com.example.marluse.entrega.model.Entrega;
import com.example.marluse.estoque.dto.ProdutoResponse;
import com.example.marluse.estoque.model.Produto;
import com.example.marluse.estoque.repository.ProdutoRepository;
import com.example.marluse.financeiro.enums.StatusLancamento;
import com.example.marluse.financeiro.repository.LancamentoFinanceiroRepository;
import com.example.marluse.financeiro.service.LancamentoFinanceiroService;
import com.example.marluse.locacoes.dto.ItemLocacaoRequest;
import com.example.marluse.locacoes.dto.ItemLocacaoResponse;
import com.example.marluse.locacoes.dto.LocacaoEdicaoRequest;
import com.example.marluse.locacoes.dto.LocacaoRequest;
import com.example.marluse.locacoes.dto.LocacaoResponse;
import com.example.marluse.locacoes.enums.StatusLocacao;
import com.example.marluse.locacoes.model.ItemLocacao;
import com.example.marluse.locacoes.model.Locacao;
import com.example.marluse.locacoes.repository.LocacaoRepository;
import com.example.marluse.vendas.enums.FormaPagamento;
import com.example.marluse.vendas.enums.TipoDesconto;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.example.marluse.financeiro.dto.ParcelaResponse;
import com.example.marluse.financeiro.model.LancamentoFinanceiro;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LocacaoService {

    private final LocacaoRepository locacaoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final LancamentoFinanceiroService lancamentoService;
    private final LancamentoFinanceiroRepository lancamentoRepository;

    @Transactional
    public LocacaoResponse criar(LocacaoRequest request, boolean isOrcamento){
        Cliente cliente = null;
        if (request.clienteId() != null) {
            cliente = clienteRepository.findById(request.clienteId())
                    .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado"));
        }

        if (!request.dataDevolucaoPrevista().isAfter(request.dataRetirada())){
            throw new IllegalArgumentException("A data de devolução deve ser posterior à data de retirada");
        }

        // Status inicial: query param tem prioridade; fallback para campo do body ou ATIVA
        StatusLocacao statusInicial = isOrcamento ? StatusLocacao.ORCAMENTO
                : (request.status() != null ? request.status() : StatusLocacao.ATIVA);

        // Calcula os dias entre data de retirada e data de devolução prevista
        long dias = ChronoUnit.DAYS.between(request.dataRetirada(), request.dataDevolucaoPrevista());

        Locacao locacao = Locacao.builder()
                .status(statusInicial)
                .formaPagamento(request.formaPagamento())
                .dataRetirada(request.dataRetirada())
                .dataDevolucaoPrevista(request.dataDevolucaoPrevista())
                .observacao(request.observacao())
                .valorTotal(BigDecimal.ZERO)
                .build();

        locacao.setCliente(cliente);

        BigDecimal total = BigDecimal.ZERO;

        for (ItemLocacaoRequest itemRequest : request.itens()){
            Produto produto = produtoRepository.findById(itemRequest.produtoId())
                    .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));

            // Orçamento não reserva estoque
            if (statusInicial != StatusLocacao.ORCAMENTO) {
                if (produto.getQuantidadeEstoque() < itemRequest.quantidade()){
                    throw new IllegalArgumentException("Estoque insuficiente para: " + produto.getNome());
                }
                produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - itemRequest.quantidade());
                produtoRepository.save(produto);
            }

            BigDecimal subtotal = produto.getPreco()
                    .multiply(BigDecimal.valueOf(itemRequest.quantidade()))
                    .multiply(BigDecimal.valueOf(dias));

            ItemLocacao item = ItemLocacao.builder()
                    .locacao(locacao)
                    .produto(produto)
                    .quantidade(itemRequest.quantidade())
                    .precoDiaria(produto.getPreco())
                    .subtotal(subtotal)
                    .build();

            locacao.getItens().add(item);
            total = total.add(subtotal);
        }


        locacao.setDesconto(request.desconto());
        locacao.setTipoDesconto(request.tipoDesconto());
        if (request.desconto() != null) locacao.setDescontoAplicadoEm(LocalDate.now());
        if (request.entrega() != null ) locacao.setEntrega(new Entrega( null, locacao, request.entrega().endereco(), request.entrega().dataPrevista(), null, StatusEntrega.PENDENTE));
        BigDecimal valorFinal = aplicarDesconto(total, request.desconto(), request.tipoDesconto());

        locacao.setValorTotal(valorFinal);



        // Gera número sequencial antes de salvar
        long numeroLocacao = locacaoRepository.count() + 1;
        locacao.setNumero(numeroLocacao);

        Locacao locacaoSalva = locacaoRepository.save(locacao);

        // Integração financeira — apenas para locações confirmadas (não orçamento)
        if (statusInicial != StatusLocacao.ORCAMENTO) {
            String nomeCliente = cliente != null ? cliente.getNome() : "Consumidor Final";

            int parcelas = request.numeroParcelas() != null && request.numeroParcelas() > 1
                    ? request.numeroParcelas() : 1;
            // Locações sempre iniciam PENDENTE — o pagamento é confirmado na devolução
            String descricao = request.formaPagamento() == FormaPagamento.FIADO
                    ? String.format("Locação fiado #%03d - %s", numeroLocacao, nomeCliente)
                    : String.format("Locação #%03d - %s", numeroLocacao, nomeCliente);
            LocalDate vencimento = request.primeiroVencimento() != null ? request.primeiroVencimento()
                    : locacaoSalva.getDataDevolucaoPrevista().plusDays(1);

            lancamentoService.registarLocacaoReceita(locacaoSalva, descricao, valorFinal, StatusLancamento.PENDENTE, vencimento, parcelas);
        }

        return toResponse(locacaoSalva);
    }

    public List<LocacaoResponse> listarTodas(){
        return locacaoRepository.findAll()
                .stream()
                .map(LocacaoResponse::from)
                .toList();
    }

    public LocacaoResponse listarPorId(String id) {
        Locacao locacao = locacaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Locação não encontrada"));
        List<ParcelaResponse> parcelas = listarParcelas(id);
        return LocacaoResponse.from(locacao, parcelas);
    }

    public List<LocacaoResponse> listarPorStatus(StatusLocacao status) {
        return locacaoRepository.findByStatus(status)
                .stream()
                .map(LocacaoResponse::from)
                .toList();
    }

    public List<ParcelaResponse> listarParcelas(String locacaoId) {
        return lancamentoRepository.findByLocacaoId(locacaoId).stream()
                .sorted(Comparator.comparing(LancamentoFinanceiro::getDataVencimento,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ParcelaResponse::from)
                .toList();
    }

    public List<LocacaoResponse> listarAtrasadas() {
        return locacaoRepository.findByStatusAndDataDevolucaoPrevistaBefore(StatusLocacao.ATIVA, LocalDate.now())
                .stream()
                .map(LocacaoResponse::from)
                .toList();
    }

    @Transactional
    public LocacaoResponse editar(String id, LocacaoEdicaoRequest request) {
        Locacao locacao = locacaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Locação não encontrada"));

        if (locacao.getStatus() == StatusLocacao.CANCELADA || locacao.getStatus() == StatusLocacao.DEVOLVIDA) {
            throw new IllegalArgumentException("Não é possível editar uma locação " + locacao.getStatus().name().toLowerCase());
        }

        if (request.desconto() != null) {
            locacao.setDesconto(request.desconto());
            locacao.setTipoDesconto(request.tipoDesconto());
            locacao.setDescontoAplicadoEm(LocalDate.now());
            // Aplica desconto sobre o valorTotal atual (cumulativo)
            BigDecimal novoTotal = aplicarDesconto(locacao.getValorTotal(), request.desconto(), request.tipoDesconto());
            locacao.setValorTotal(novoTotal);

            // Redistribui valor entre parcelas ainda não pagas
            List<LancamentoFinanceiro> pendentes =
                    lancamentoRepository.findByLocacaoIdAndStatusNot(id, StatusLancamento.PAGO);
            if (!pendentes.isEmpty()) {
                BigDecimal pago = lancamentoRepository.findByLocacaoId(id).stream()
                        .filter(l -> l.getStatus() == StatusLancamento.PAGO)
                        .map(LancamentoFinanceiro::getValor)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal restante = novoTotal.subtract(pago);
                if (restante.compareTo(BigDecimal.ZERO) < 0)
                    throw new IllegalArgumentException("Desconto resulta em valor menor que o já pago");
                BigDecimal valorParcela = restante.divide(
                        BigDecimal.valueOf(pendentes.size()), 2, RoundingMode.HALF_UP);
                pendentes.forEach(l -> {
                    l.setValor(valorParcela);
                    lancamentoRepository.save(l);
                });
            }
        }

        locacao.setFormaPagamento(request.formaPagamento());
        locacao.setObservacao(request.observacao());

        return LocacaoResponse.from(locacaoRepository.save(locacao));
    }

    @Transactional
    public LocacaoResponse devolver(String id){
        Locacao locacao = locacaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Locação não encontrada!"));

        if (locacao.getStatus() != StatusLocacao.ATIVA && locacao.getStatus() != StatusLocacao.ATRASADA){
            throw new IllegalArgumentException("Locação não pode ser devolvida no status atual");
        }

        for (ItemLocacao item : locacao.getItens()){
            Produto produto = item.getProduto();
            produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() + item.getQuantidade());
            produtoRepository.save(produto);
        }

        lancamentoRepository.findByLocacaoId(id).forEach(l -> {
            if (l.getStatus() != StatusLancamento.PAGO) {
                l.setStatus(StatusLancamento.PAGO);
                l.setDataPagamento(LocalDate.now());
                lancamentoRepository.save(l);
            }
        });

        locacao.setStatus(StatusLocacao.DEVOLVIDA);
        locacao.setDataDevolucaoReal(LocalDate.now());
        return LocacaoResponse.from(locacaoRepository.save(locacao));
    }

    @Transactional
    public void deletar(String id) {
        Locacao locacao = locacaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Locação não encontrada"));

        // Restaura estoque se ainda estava em uso
        if (locacao.getStatus() == StatusLocacao.ATIVA || locacao.getStatus() == StatusLocacao.ATRASADA) {
            for (ItemLocacao item : locacao.getItens()) {
                Produto produto = item.getProduto();
                produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() + item.getQuantidade());
                produtoRepository.save(produto);
            }
        }

        // Remove lançamentos financeiros vinculados antes de apagar a locação
        lancamentoRepository.deleteByLocacaoId(id);

        locacaoRepository.delete(locacao);
    }

    @Transactional
    public LocacaoResponse cancelar(String id){
        Locacao locacao = locacaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Locação não encontrada"));

        if (locacao.getStatus() == StatusLocacao.CANCELADA){
            throw new IllegalArgumentException("A locação já está cancelada");
        }

        if (locacao.getStatus() == StatusLocacao.ATIVA || locacao.getStatus() == StatusLocacao.ATRASADA) {

            for (ItemLocacao item : locacao.getItens()) {
                Produto produto = item.getProduto();
                produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() + item.getQuantidade());
                produtoRepository.save(produto);
            }
        }

        lancamentoRepository.findByLocacaoId(id).forEach(l -> {
            l.setStatus(StatusLancamento.CANCELADO);
            lancamentoRepository.save(l);
        });

        locacao.setStatus(StatusLocacao.CANCELADA);
        return LocacaoResponse.from(locacaoRepository.save(locacao));
    }

    private BigDecimal aplicarDesconto(BigDecimal bruto, BigDecimal desconto, TipoDesconto tipo) {
        if (desconto == null || desconto.compareTo(BigDecimal.ZERO) == 0 ) return bruto;

        BigDecimal calc = tipo == TipoDesconto.PERCENTUAL
                ? bruto.multiply(desconto).divide(BigDecimal.valueOf(100))
                : desconto;

        BigDecimal resulto = bruto.subtract(calc);

        if (resulto.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Desconto não pode ser maior que o valor total");
        return resulto;
    }

    @Transactional
    public LocacaoResponse confirmar(String id) {
        Locacao locacao = locacaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Locação não encontrada"));

        if (locacao.getStatus() != StatusLocacao.ORCAMENTO) {
            throw new IllegalArgumentException("Apenas orçamentos podem ser confirmados");
        }

        // Reserva estoque
        for (ItemLocacao item : locacao.getItens()) {
            Produto produto = item.getProduto();
            if (produto.getQuantidadeEstoque() < item.getQuantidade()) {
                throw new IllegalArgumentException("Estoque insuficiente para: " + produto.getNome());
            }
            produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - item.getQuantidade());
            produtoRepository.save(produto);
        }

        // Cria lançamento PENDENTE (confirmado na devolução)
        String nomeCliente = locacao.getCliente() != null ? locacao.getCliente().getNome() : "Consumidor Final";
        String descricao = locacao.getFormaPagamento() == FormaPagamento.FIADO
                ? String.format("Locação fiado #%03d - %s", locacao.getNumero(), nomeCliente)
                : String.format("Locação #%03d - %s", locacao.getNumero(), nomeCliente);
        LocalDate vencimento = locacao.getDataDevolucaoPrevista().plusDays(1);

        lancamentoService.registarLocacaoReceita(locacao, descricao, locacao.getValorTotal(), StatusLancamento.PENDENTE, vencimento, 1);

        // ATIVA ou já ATRASADA se a data prevista passou
        StatusLocacao novoStatus = locacao.getDataDevolucaoPrevista().isBefore(LocalDate.now())
                ? StatusLocacao.ATRASADA
                : StatusLocacao.ATIVA;
        locacao.setStatus(novoStatus);

        return LocacaoResponse.from(locacaoRepository.save(locacao));
    }

    public BigDecimal somarLocacoesPorPeriodo(LocalDate inicio, LocalDate fim) {
        return lancamentoRepository.somarReceitaLocacoesPorPagamento(inicio, fim);
    }

    private LocacaoResponse toResponse(Locacao locacao) {
        return LocacaoResponse.from(locacao);
    }
}
