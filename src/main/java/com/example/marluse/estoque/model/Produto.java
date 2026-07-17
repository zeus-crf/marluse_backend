package com.example.marluse.estoque.model;

import com.example.marluse.estoque.dto.CategoriaProduto;
import com.example.marluse.estoque.enums.UnidadeMedida;
import com.example.marluse.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "produtos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produto extends BaseEntity {

    @Column(nullable = false)
    private String nome;

    private String descricao;

    @Column(nullable = false)
    private BigDecimal valorCompra;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(name = "preco_diaria", precision = 10, scale = 2)
    private BigDecimal precoDiaria;

    @Builder.Default
    @Column(name = "quantidade_estoque", nullable = false)
    private Integer quantidadeEstoque = 0;

    @Builder.Default
    @Column(name = "estoque_minimo")
    private Integer estoqueMinimo = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean ativo = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    UnidadeMedida medida;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", length = 50)
    private CategoriaProduto categoria;

    @Builder.Default
    @Column(nullable = false)
    private boolean rascunho = false;

}
