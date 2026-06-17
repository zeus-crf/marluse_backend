package com.example.marluse.financeiro.dto;

import com.example.marluse.financeiro.enums.StatusLancamento;
import com.example.marluse.financeiro.enums.TipoLancamento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LancamentoFinanceiroRequest(
        @NotNull(message = "O tipo de lançamento é obrigatório")
        TipoLancamento tipo,

        @NotNull(message = "A categoria não pode ser nula")
        String categoria,

        @NotNull(message = "A descrição não pode ser nula")
        String descricao,

        @NotNull(message = "O valor não pode ser nulo")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        BigDecimal valor,

        @NotNull(message = "O status não pode ser nulo")
        StatusLancamento status,

        LocalDate dataVencimento,

        LocalDate dataPagamento
) {
}
