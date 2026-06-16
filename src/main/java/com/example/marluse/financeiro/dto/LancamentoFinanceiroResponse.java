package com.example.marluse.financeiro.dto;

import com.example.marluse.financeiro.enums.StatusLancamento;
import com.example.marluse.financeiro.enums.TipoLancamento;
import com.example.marluse.financeiro.model.LancamentoFinanceiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LancamentoFinanceiroResponse(
        String id,
        TipoLancamento tipo,
        String categoria,
        String descricao,
        BigDecimal valor,
        LocalDate dataVencimento,
        LocalDate dataPagamento,
        StatusLancamento status,
        String pedidoId,
        String locacaoId,
        LocalDateTime createdAt
) {
    public static LancamentoFinanceiroResponse from(LancamentoFinanceiro l){
        return new LancamentoFinanceiroResponse(
                l.getId(),
                l.getTipo(),
                l.getCategoria(),
                l.getDescricao(),
                l.getValor(),
                l.getDataVencimento(),
                l.getDataPagamento(),
                l.getStatus(),
                l.getPedido() != null ? l.getPedido().getId() : null,
                l.getLocacao() != null ? l.getLocacao().getId() : null,
                l.getCreatedAt()
        );
    }
}
