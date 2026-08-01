package com.example.marluse.locacoes.dto;

import com.example.marluse.locacoes.enums.UnidadeCobranca;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ItemLocacaoRequest (

        String produtoId,

        String produtoNome,

        @NotNull(message = "Quantidade é obrigatória")
        @DecimalMin(value = "0", inclusive = false, message = "Quantidade deve ser maior que zero")
        BigDecimal quantidade,

        BigDecimal precoDiaria,

        UnidadeCobranca unidadeCobranca,

        boolean baixarEstoque,

        boolean permitirSemEstoque
) {

    public boolean isProdutoNovo() {
        return (produtoId == null || produtoId.isBlank())
                    && (produtoNome != null && !produtoNome.isBlank());
    }
}
