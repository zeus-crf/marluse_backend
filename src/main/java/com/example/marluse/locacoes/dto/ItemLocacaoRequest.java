package com.example.marluse.locacoes.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ItemLocacaoRequest (

        String produtoId,

        String produtoNome,

        @NotNull(message = "Quantidade é obrigatória")
        @Min(value = 1, message = "Quantidade deve ser maior que zero")
        Integer quantidade,

        BigDecimal precoDiaria,

        boolean baixarEstoque,

        boolean permitirSemEstoque
) {

    public boolean isProdutoNovo() {
        return (produtoId == null || produtoId.isBlank())
                    && (produtoNome != null || !produtoNome.isBlank());
    }
}
