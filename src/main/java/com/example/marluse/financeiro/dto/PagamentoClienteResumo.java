package com.example.marluse.financeiro.dto;

import com.example.marluse.financeiro.model.PagamentoCliente;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PagamentoClienteResumo(
        String id,
        BigDecimal valor,
        LocalDate data,
        String observacao,
        boolean estornado
) {
    public static PagamentoClienteResumo from(PagamentoCliente p) {
        return new PagamentoClienteResumo(p.getId(), p.getValor(), p.getData(), p.getObservacao(), p.isEstornado());
    }
}