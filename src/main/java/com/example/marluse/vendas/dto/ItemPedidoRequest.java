package com.example.marluse.vendas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ItemPedidoRequest(

        String productId,

        String produtoNome,

        @NotNull(message = "Quantidade é obrigatória")
        @Min(value = 1, message = "A Quantidade deve ser maior que zero")
        Integer quantidade,

        BigDecimal preco,

        boolean baixarEstoque,

        boolean permitirSemEstoque
) {

    private boolean isProdutoNovo() {
        return  (productId == null || productId.isBlank())
                    && produtoNome != null & !produtoNome.isBlank();
    }
}
