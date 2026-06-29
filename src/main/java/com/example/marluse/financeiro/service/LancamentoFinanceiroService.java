package com.example.marluse.financeiro.service;

import com.example.marluse.financeiro.dto.LancamentoAtualizarRequest;
import com.example.marluse.financeiro.dto.LancamentoFinanceiroRequest;
import com.example.marluse.financeiro.dto.LancamentoFinanceiroResponse;
import com.example.marluse.financeiro.dto.ResumoDiaResponse;
import com.example.marluse.financeiro.enums.StatusLancamento;
import com.example.marluse.financeiro.enums.TipoLancamento;
import com.example.marluse.financeiro.model.LancamentoFinanceiro;
import com.example.marluse.financeiro.repository.LancamentoFinanceiroRepository;
import com.example.marluse.locacoes.model.Locacao;
import com.example.marluse.vendas.model.Pedido;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LancamentoFinanceiroService {

    private final LancamentoFinanceiroRepository lancamentoFinanceiroRepository;

    @Transactional
    public LancamentoFinanceiroResponse criar (LancamentoFinanceiroRequest request){
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.builder()
                .tipo(request.tipo())
                .categoria(request.categoria())
                .descricao(request.descricao())
                .valor(request.valor())
                .dataVencimento(request.dataVencimento())
                .dataPagamento(request.dataPagamento())
                .status(request.status() != null ? request.status(): StatusLancamento.PENDENTE)
                .build();

        return LancamentoFinanceiroResponse.from(lancamentoFinanceiroRepository.save(lancamento));
    }

    public List<LancamentoFinanceiroResponse> listarTodos(){
        return lancamentoFinanceiroRepository.findAll()
                .stream()
                .map(LancamentoFinanceiroResponse::from)
                .toList();
    }

    public LancamentoFinanceiroResponse listarPorId(String id){
        return lancamentoFinanceiroRepository.findById(id)
                .map(LancamentoFinanceiroResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Lançamento Financeiro não encontrado!"));
    }

    public List<LancamentoFinanceiroResponse> listarPendentes(){
        return lancamentoFinanceiroRepository.findByStatus(StatusLancamento.PENDENTE)
                .stream()
                .map(this::toResponse)
                .toList();

    }

    public List<LancamentoFinanceiroResponse> listarVencidos() {
        return lancamentoFinanceiroRepository.findVencidos(LocalDate.now())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LancamentoFinanceiroResponse pagar(String id){
        LancamentoFinanceiro lancamento = lancamentoFinanceiroRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Lançamento Financeiro não encontrado"));

        if (lancamento.getStatus() == StatusLancamento.PAGO){
            throw new IllegalArgumentException( "Esse lançamento já está pago");
        }

        lancamento.setStatus(StatusLancamento.PAGO);
        lancamento.setDataPagamento(LocalDate.now());

        return LancamentoFinanceiroResponse.from(lancamentoFinanceiroRepository.save(lancamento));

    }


    public ResumoDiaResponse resumoDia(){
        LocalDate hoje = LocalDate.now();
        BigDecimal receitas = lancamentoFinanceiroRepository.somarPorTipoEData(TipoLancamento.RECEITA, hoje);
        BigDecimal despesas = lancamentoFinanceiroRepository.somarPorTipoEData(TipoLancamento.DESPESA, hoje);

        BigDecimal saldo = receitas.subtract(despesas);

        return new ResumoDiaResponse(hoje, receitas, despesas, saldo);

    }


    @Transactional
    public LancamentoFinanceiroResponse atualizar(String id, LancamentoAtualizarRequest request) {
        LancamentoFinanceiro lancamento = buscarEntidade(id);

        if (request.tipo()           != null) lancamento.setTipo(request.tipo());
        if (request.categoria()      != null) lancamento.setCategoria(request.categoria());
        if (request.descricao()      != null) lancamento.setDescricao(request.descricao());
        if (request.valor()          != null) lancamento.setValor(request.valor());
        if (request.status()         != null) lancamento.setStatus(request.status());
        if (request.dataVencimento() != null) lancamento.setDataVencimento(request.dataVencimento());
        if (request.dataPagamento()  != null) lancamento.setDataPagamento(request.dataPagamento());

        return LancamentoFinanceiroResponse.from(lancamentoFinanceiroRepository.save(lancamento));
    }

    @Transactional
    public void deletar(String id) {

        lancamentoFinanceiroRepository.deleteById(id);
    }


    public void registrarVendaReceita(Pedido pedido, String descricao, BigDecimal valor,
                                      StatusLancamento status, LocalDate dataVencimento){
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.builder()
                .tipo(TipoLancamento.RECEITA)
                .categoria("Venda")
                .descricao(descricao)
                .valor(valor)
                .status(status)
                .dataVencimento(dataVencimento)
                .dataPagamento(status == StatusLancamento.PAGO ? LocalDate.now() : null)
                .pedido(pedido)
                .build();

        lancamentoFinanceiroRepository.save(lancamento);
    }

    public void registarLocacaoReceita(Locacao locacao, String descricao, BigDecimal valor,
                                       StatusLancamento status, LocalDate dataVencimento){
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.builder()
                .tipo(TipoLancamento.RECEITA)
                .categoria("Locação")
                .descricao(descricao)
                .valor(valor)
                .status(status)
                .dataVencimento(dataVencimento)
                .dataPagamento(status == StatusLancamento.PAGO ? LocalDate.now() : null)
                .locacao(locacao)
                .build();

        lancamentoFinanceiroRepository.save(lancamento);
    }

    private LancamentoFinanceiro buscarEntidade(String id) {
        return lancamentoFinanceiroRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Lançamento não encontrado: " + id));
    }

    private LancamentoFinanceiroResponse toResponse(LancamentoFinanceiro l) {
        return new LancamentoFinanceiroResponse(
                l.getId(), l.getTipo(), l.getCategoria(), l.getDescricao(),
                l.getValor(), l.getDataVencimento(), l.getDataPagamento(), l.getStatus(),
                l.getPedido() != null ? l.getPedido().getId() : null,
                l.getLocacao() != null ? l.getLocacao().getId() : null,
                l.getCreatedAt()
        );
    }


}
