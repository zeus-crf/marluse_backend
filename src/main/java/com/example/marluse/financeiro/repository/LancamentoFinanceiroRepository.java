package com.example.marluse.financeiro.repository;

import com.example.marluse.financeiro.dto.LancamentoFinanceiroResponse;
import com.example.marluse.financeiro.enums.StatusLancamento;
import com.example.marluse.financeiro.enums.TipoLancamento;
import com.example.marluse.financeiro.model.LancamentoFinanceiro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LancamentoFinanceiroRepository extends JpaRepository<LancamentoFinanceiro, String> {

    List<LancamentoFinanceiro> findByStatus(StatusLancamento status);

    Optional<LancamentoFinanceiro> findByPedidoId(String pedidoId);

    Optional<LancamentoFinanceiro> findByLocacaoId(String locacaoId);

    void deleteByLocacaoId(String locacaoId);

    List<LancamentoFinanceiro> findByDataVencimentoBetween(LocalDate inicio, LocalDate fim);

    @Query("SELECT COALESCE(SUM(l.valor), 0) FROM LancamentoFinanceiro l WHERE l.tipo = :tipo AND l.status = 'PAGO' AND l.dataPagamento = :data")
    BigDecimal somarPorTipoEData(TipoLancamento tipo, LocalDate data);

    @Query("SELECT l FROM LancamentoFinanceiro l WHERE l.status = 'PENDENTE' AND l.dataVencimento < :hoje")
    List<LancamentoFinanceiro> findVencidos(LocalDate hoje);

    @Query("SELECT COALESCE(SUM(l.valor), 0) FROM LancamentoFinanceiro l WHERE l.tipo = 'RECEITA' AND l.status = 'PAGO' AND l.dataPagamento BETWEEN :inicio AND :fim")
    BigDecimal somarReceitaPorPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("""
    SELECT l FROM LancamentoFinanceiro l
    WHERE l.recorrencia IS NOT NULL
    AND l.recorrenciaAtiva = true
    AND l.dataVencimento = (
        SELECT MAX(l2.dataVencimento)
        FROM LancamentoFinanceiro l2
        WHERE l2.recorrenciaGrupoId = l.recorrenciaGrupoId
    )
    """)
    List<LancamentoFinanceiro> findUltimosPorGrupoAtivo();

    List<LancamentoFinanceiro> findByRecorrenciaGrupoId(String recorrenciaGrupoId);


}
