package com.example.marluse.locacoes.service;

import com.example.marluse.clientes.model.Cliente;
import com.example.marluse.clientes.repository.ClienteRepository;
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
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

        locacao.setValorTotal(total);
        Locacao locacaoSalva = locacaoRepository.save(locacao);

        // Integração financeira — apenas para locações confirmadas (não orçamento)
        if (statusInicial != StatusLocacao.ORCAMENTO) {
            String nomeCliente = cliente != null ? cliente.getNome() : "Consumidor Final";
            if (request.formaPagamento() == FormaPagamento.FIADO) {
                lancamentoService.registarLocacaoReceita(
                        locacaoSalva,
                        "Locação fiado - " + nomeCliente,
                        total,
                        StatusLancamento.PENDENTE,
                        locacaoSalva.getDataDevolucaoPrevista().plusDays(1)
                );
            } else {
                lancamentoService.registarLocacaoReceita(
                        locacaoSalva,
                        "Locação - " + nomeCliente,
                        total,
                        StatusLancamento.PAGO,
                        LocalDate.now()
                );
            }
        }

        return toResponse(locacaoSalva);
    }

    public List<LocacaoResponse> listarTodas(){
        return locacaoRepository.findAll()
                .stream()
                .map(LocacaoResponse::from)
                .toList();
    }

    public LocacaoResponse listarPorId(String id){
        return locacaoRepository.findById(id)
                .map(LocacaoResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Locação não encontrada"));
    }

    public List<LocacaoResponse> listarPorStatus(StatusLocacao status) {
        return locacaoRepository.findByStatus(status)
                .stream()
                .map(LocacaoResponse::from)
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

        locacao.setStatus(StatusLocacao.CANCELADA);
        return LocacaoResponse.from(locacaoRepository.save(locacao));
    }

    private LocacaoResponse toResponse (Locacao locacao){

        List<ItemLocacaoResponse> itens = locacao.getItens().stream()
                .map(item -> new ItemLocacaoResponse(
                        item.getId(),
                        item.getProduto().getId(),
                        item.getProduto().getNome(),
                        item.getQuantidade(),
                        item.getPrecoDiaria(),
                        item.getSubtotal()
                ))
                .toList();
        return new LocacaoResponse(
                locacao.getId(),
                locacao.getCliente() != null ? locacao.getCliente().getId() : null,
                locacao.getCliente() != null ? locacao.getCliente().getNome() : "Consumidor Final",
                locacao.getStatus(),
                locacao.getFormaPagamento(),
                locacao.getDataRetirada(),
                locacao.getDataDevolucaoPrevista(),
                locacao.getDataDevolucaoReal(),
                locacao.getValorTotal(),
                locacao.getObservacao(),
                itens,
                locacao.getCreatedAt()

        );
    }
}
