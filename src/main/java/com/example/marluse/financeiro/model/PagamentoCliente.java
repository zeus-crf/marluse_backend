package com.example.marluse.financeiro.model;

import com.example.marluse.clientes.model.Cliente;
import com.example.marluse.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pagamentos_clientes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagamentoCliente extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate data;

    private String observacao;

    @Builder.Default
    @Column(nullable = false)
    private boolean estornado = false;

    @Column(name = "estornado_em")
    private LocalDateTime estotnadoEm;

    @Builder.Default
    @OneToMany(mappedBy = "pagamentoCliente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AbatimentoParcela> abatimentos = new ArrayList<>();
}
