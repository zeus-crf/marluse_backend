package com.example.marluse.relatorios.dto;

import java.math.BigDecimal;

public record TopProdutoResponse(
        String nome,
        long quantidade,
        BigDecimal lucro,
        BigDecimal custo,
        BigDecimal total
) {}
