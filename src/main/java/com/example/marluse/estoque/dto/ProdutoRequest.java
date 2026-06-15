package com.example.marluse.estoque.dto;

import com.example.marluse.estoque.enums.UnidadeMedida;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProdutoRequest(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        String descricao,

        @NotNull(message = "Preço é obrigatório")
        @DecimalMin(value = "0.0", inclusive = false, message = "Preço deve ser maior que zero")
        BigDecimal preco,

        @Min(value = 0, message = "Quantidade não pode ser negativa")
        Integer quantidadeEstoque,

        @Min(value = 0, message = "Estoque mínimo não pode ser negativo")
        Integer estoqueMinimo,

        @NotNull(message = "Unidade de medida é obrigatória")
        UnidadeMedida medida
) {
}
