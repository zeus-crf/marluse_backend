package com.example.marluse.locacoes.dto;

import com.example.marluse.locacoes.model.Locacao;
import com.example.marluse.locacoes.enums.StatusLocacao;
import com.example.marluse.vendas.enums.FormaPagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record LocacaoResponse(
        String id,
        String clienteId,
        String clienteNome,
        StatusLocacao status,
        FormaPagamento formaPagamento,
        LocalDate dataRetirada,
        LocalDate dataDevolucaoPrevista,
        LocalDate dataDevolucaoReal,
        BigDecimal valorTotal,
        String observacao,
        List<ItemLocacaoResponse> itens,
        LocalDateTime createdAt
) {
    public static LocacaoResponse from(Locacao locacao) {
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
                locacao.getItens().stream().map(ItemLocacaoResponse::from).toList(),
                locacao.getCreatedAt()
        );
    }
}