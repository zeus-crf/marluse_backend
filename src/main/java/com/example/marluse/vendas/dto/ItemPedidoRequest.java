package com.example.marluse.vendas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ItemPedidoRequest(
        @NotNull(message = "Produto é obrigatório")
        String productId,

        @NotNull(message = "Quantidade é obrigatória")
        @Min(value = 1, message = "A Quantidade deve ser maior que zero")
        Integer quantidade
) {
}
