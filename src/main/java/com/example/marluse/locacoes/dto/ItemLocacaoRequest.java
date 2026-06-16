package com.example.marluse.locacoes.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ItemLocacaoRequest (
        @NotNull(message = "Produto é obrigatório")
        String produtoId,

        @NotNull(message = "Quantidade é obrigatória")
        @Min(value = 1, message = "Quantidade deve ser maior que zero")
        Integer quantidade
) {
}
