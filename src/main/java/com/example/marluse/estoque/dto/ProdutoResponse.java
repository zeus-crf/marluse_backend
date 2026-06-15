package com.example.marluse.estoque.dto;

import com.example.marluse.estoque.enums.UnidadeMedida;
import com.example.marluse.estoque.model.Produto;

import java.math.BigDecimal;

public record ProdutoResponse(
        String id,
        String nome,
        String descricao,
        BigDecimal preco,
        Integer quantidadeEstoque,
        Integer estoqueMinimo,
        boolean ativo,
        boolean estoqueBaixo,
        UnidadeMedida medida
) {
    public static ProdutoResponse from(Produto produto){
        return new ProdutoResponse(
                produto.getId(),
                produto.getNome(),
                produto.getDescricao(),
                produto.getPreco(),
                produto.getQuantidadeEstoque(),
                produto.getEstoqueMinimo(),
                produto.isAtivo(),
                produto.getQuantidadeEstoque() <= produto.getEstoqueMinimo(),
                produto.getMedida()
        );
    }
}
