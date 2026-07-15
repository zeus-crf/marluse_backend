package com.example.marluse.locacoes.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ItemLocacaoRequest (
        @NotNull(message = "Produto é obrigatório")
        String produtoId,

        @NotNull(message = "Quantidade é obrigatória")
        @Min(value = 1, message = "Quantidade deve ser maior que zero")
        Integer quantidade,

        BigDecimal precoDiaria
) {
}
