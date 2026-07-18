package com.example.marluse.clientes.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ClienteSaldoResponse(
        BigDecimal saldoDevolver,
        List<ItemDevido> itens
) {
    public record ItemDevido(
            String origemTipo,   // "PEDIDO" ou "LOCACAO"
            String origemId,
            Long numero,
            LocalDate data,
            BigDecimal valorTotal,
            BigDecimal valorPago,
            BigDecimal saldo
    ){}
}
