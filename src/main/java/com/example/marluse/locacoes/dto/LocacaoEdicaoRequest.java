package com.example.marluse.locacoes.dto;

import com.example.marluse.vendas.enums.FormaPagamento;
import jakarta.validation.constraints.NotNull;

public record LocacaoEdicaoRequest(

        @NotNull(message = "Forma de pagamento é obrigatória")
        FormaPagamento formaPagamento,

        String observacao

) {}
