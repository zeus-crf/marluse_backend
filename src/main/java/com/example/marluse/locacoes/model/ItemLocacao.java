package com.example.marluse.locacoes.model;

import com.example.marluse.estoque.model.Produto;
import com.example.marluse.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "itens_locacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemLocacao extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locacao_id", nullable = false)
    private Locacao locacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(name = "preco_diaria", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoDiaria;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;
}
