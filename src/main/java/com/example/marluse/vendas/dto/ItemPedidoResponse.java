package com.example.marluse.vendas.dto;

import com.example.marluse.vendas.model.ItemPedido;

import java.math.BigDecimal;

public record ItemPedidoResponse(
        String id,
        String produtoId,
        String produtoNome,
        Integer quantidade,
        BigDecimal precoUnitario,
        BigDecimal subtotal
) {
    public static ItemPedidoResponse from(ItemPedido item) {
        return new ItemPedidoResponse(
                item.getId(),
                item.getProduto().getId(),
                item.getProduto().getNome(),
                item.getQuantidade(),
                item.getPrecoUnitario(),
                item.getSubTotal()
        );
    }
}