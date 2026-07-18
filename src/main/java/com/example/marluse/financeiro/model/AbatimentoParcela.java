package com.example.marluse.financeiro.model;

import com.example.marluse.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CollectionId;
import tools.jackson.core.ObjectReadContext;

import java.math.BigDecimal;

@Entity
@Table(name = "abatimentos")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbatimentoParcela extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pagamento_cliente_id", nullable = false)
    private PagamentoCliente pagamentoCliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lancamento_id", nullable = false)
    private LancamentoFinanceiro lancamento;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;
}
