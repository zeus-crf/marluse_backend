package com.example.marluse.vendas.dto;

import com.example.marluse.vendas.enums.FormaPagamento;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PedidoRequest(

        String clienteId,

        @NotNull(message = "Forma de pagamento é obrigatória")
        FormaPagamento formaPagamento,

        @NotEmpty(message = "Pedido deve ter ao menos um item")
        List<ItemPedidoRequest> itens,

        String observacao
) {}