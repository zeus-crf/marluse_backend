package com.example.marluse.estoque.dto;

import com.example.marluse.estoque.enums.UnidadeMedida;
import com.example.marluse.estoque.model.Produto;

import java.math.BigDecimal;

public record ProdutoResponse(
        String id,
        String nome,
        String descricao,
        BigDecimal valorCompra,
        BigDecimal preco,
        BigDecimal precoDiaria,
        BigDecimal quantidadeEstoque,
        Integer estoqueMinimo,
        boolean ativo,
        boolean estoqueBaixo,
        UnidadeMedida medida,
        CategoriaProduto categoria,
        boolean rascunho
) {
    public static ProdutoResponse from(Produto produto){
        return new ProdutoResponse(
                produto.getId(),
                produto.getNome(),
                produto.getDescricao(),
                produto.getValorCompra(),
                produto.getPreco(),
                produto.getPrecoDiaria(),
                produto.getQuantidadeEstoque(),
                produto.getEstoqueMinimo(),
                produto.isAtivo(),
                produto.getQuantidadeEstoque().compareTo(BigDecimal.valueOf(produto.getEstoqueMinimo())) <= 0,
                produto.getMedida(),
                produto.getCategoria(),
                produto.isRascunho()
        );
    }
}
