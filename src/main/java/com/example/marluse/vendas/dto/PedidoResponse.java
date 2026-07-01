package com.example.marluse.vendas.dto;

import com.example.marluse.vendas.enums.TipoDesconto;
import com.example.marluse.vendas.model.Pedido;
import com.example.marluse.vendas.enums.FormaPagamento;
import com.example.marluse.vendas.enums.StatusPedido;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(
        String id,
        Long numero,
        String clienteId,
        String clienteNome,
        StatusPedido status,
        FormaPagamento formaPagamento,
        BigDecimal valorTotal,
        String observacao,
        List<ItemPedidoResponse> itens,
        LocalDateTime createdAt,
        LocalDate dataVencimento,
        BigDecimal valorBruto,
        BigDecimal desconto,
        TipoDesconto tipoDesconto
) {
    public static PedidoResponse from(Pedido pedido) {
       BigDecimal bruto = pedido.getItens().stream()
               .map(i -> i.getPrecoUnitario().multiply(BigDecimal.valueOf(i.getQuantidade())))
               .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PedidoResponse(
                pedido.getId(),
                pedido.getNumero(),
                pedido.getCliente() != null ? pedido.getCliente().getId() : null,
                pedido.getCliente() != null ? pedido.getCliente().getNome() : "Consumidor Final",
                statusEfetivo(pedido),
                pedido.getFormaPagamento(),
                pedido.getValorTotal(),
                pedido.getObservacao(),
                pedido.getItens().stream().map(ItemPedidoResponse::from).toList(),
                pedido.getCreatedAt(),
                pedido.getDataVencimento(),
                bruto,
                pedido.getDesconto(),
                pedido.getTipoDesconto()

        );
    }

    private static StatusPedido statusEfetivo(Pedido pedido) {
        if (pedido.getStatus() == StatusPedido.CONFIRMADO
                && pedido.getFormaPagamento() == FormaPagamento.FIADO
                && pedido.getDataVencimento() != null
                && pedido.getDataVencimento().isBefore(LocalDate.now())) {
            return StatusPedido.PENDENTE;
        }
        return pedido.getStatus();
    }
}