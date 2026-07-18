package com.example.marluse.financeiro.dto;

import com.example.marluse.financeiro.model.PagamentoCliente;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AbatimentoResultado(
        String pagamentoId,
        BigDecimal valorAbatido,
        BigDecimal saldoAnterior,
        BigDecimal saldoAtual,
        LocalDate data
) {
    public static AbatimentoResultado from(PagamentoCliente p, BigDecimal saldoAnterior, BigDecimal saldoAtual) {
        return new AbatimentoResultado(p.getId(), p.getValor(), saldoAnterior, saldoAtual, p.getData());
    }
}