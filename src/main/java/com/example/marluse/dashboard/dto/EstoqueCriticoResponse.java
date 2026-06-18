package com.example.marluse.dashboard.dto;

import java.math.BigDecimal;

public record EstoqueCriticoResponse(
        String id,
        String nome,
        int quantidadeAtual,
        int estoqueMinimo,
        BigDecimal preco
){
}
