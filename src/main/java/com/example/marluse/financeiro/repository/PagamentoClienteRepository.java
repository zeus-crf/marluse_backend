package com.example.marluse.financeiro.repository;

import com.example.marluse.financeiro.model.PagamentoCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PagamentoClienteRepository extends JpaRepository<PagamentoCliente, String> {

    List<PagamentoCliente> findByClienteIsOrderByDataDesc(String clienteId);

    /** Soma dos abatimentos (não estornados) recebidos no período — dinheiro que entrou no caixa. */
    @Query("SELECT COALESCE(SUM(p.valor), 0) FROM PagamentoCliente p " +
            "WHERE p.estornado = false AND p.data BETWEEN :inicio AND :fim")
    BigDecimal somarAbatimentosPorPeriodo(@Param("inicio") LocalDate inicio,
                                          @Param("fim") LocalDate fim);
}
