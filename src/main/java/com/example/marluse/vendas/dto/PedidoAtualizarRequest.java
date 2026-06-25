package com.example.marluse.vendas.dto;

import com.example.marluse.vendas.enums.FormaPagamento;
import jakarta.validation.constraints.NotNull;

public record PedidoAtualizarRequest(

        FormaPagamento formaPagamento,

        String observacao
) {}
