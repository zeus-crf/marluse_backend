package com.example.marluse.locacoes.model;

import com.example.marluse.clientes.model.Cliente;
import com.example.marluse.locacoes.enums.StatusLocacao;
import com.example.marluse.shared.BaseEntity;
import com.example.marluse.vendas.enums.FormaPagamento;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "locacoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Locacao extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    @Enumerated(EnumType.STRING)
    private StatusLocacao status;

    @Column(name = "forma_pagamento")
    @Enumerated(EnumType.STRING)
    private FormaPagamento formaPagamento;

    @Column(name = "data_retirada", nullable = false)
    private LocalDate dataRetirada;

    @Column(name = "data_devolucao_prevista", nullable = false)
    private LocalDate dataDevolucaoPrevista;

    @Column(name = "data_devolucao_real")
    private LocalDate dataDevolucaoReal;

    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    private String observacao;

    @Builder.Default
    @OneToMany(mappedBy = "locacao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ItemLocacao> itens = new ArrayList<>();


}
