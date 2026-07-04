package com.example.marluse.relatorios.service;

import com.example.marluse.financeiro.enums.StatusLancamento;
import com.example.marluse.financeiro.enums.TipoLancamento;
import com.example.marluse.financeiro.model.LancamentoFinanceiro;
import com.example.marluse.financeiro.repository.LancamentoFinanceiroRepository;
import com.example.marluse.relatorios.dto.*;
import com.example.marluse.vendas.enums.StatusPedido;
import com.example.marluse.vendas.repository.ItemPedidoRepository;
import com.example.marluse.vendas.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RelatoriosService {

    private final LancamentoFinanceiroRepository lancamentoRepository;
    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;

    // ── KPIs ──────────────────────────────────────────────────────────────────

    public KpisResponse kpis(String periodo) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioPeriodo = resolverInicio(periodo, hoje);

        BigDecimal receita     = lancamentoRepository.somarReceitaPorPeriodo(inicioPeriodo, hoje);
        BigDecimal despesas    = lancamentoRepository.somarDespesaPorPeriodo(inicioPeriodo, hoje);
        BigDecimal saldo       = receita.subtract(despesas);
        long totalPedidos      = pedidoRepository.contarVendasPorPeriodo(StatusPedido.PAGO, inicioPeriodo, hoje);
        BigDecimal ticketMedio = totalPedidos > 0
                ? receita.divide(BigDecimal.valueOf(totalPedidos), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        LocalDate inicioMesAtual    = hoje.withDayOfMonth(1);
        LocalDate inicioMesAnterior = inicioMesAtual.minusMonths(1);
        LocalDate fimMesAnterior    = inicioMesAtual.minusDays(1);

        BigDecimal receitaMes  = lancamentoRepository.somarReceitaPorPeriodo(inicioMesAtual, hoje);
        BigDecimal despesasMes = lancamentoRepository.somarDespesaPorPeriodo(inicioMesAtual, hoje);
        BigDecimal saldoMes    = receitaMes.subtract(despesasMes);
        long pedidosMes        = pedidoRepository.contarVendasPorPeriodo(StatusPedido.PAGO, inicioMesAtual, hoje);
        BigDecimal ticketMes   = pedidosMes > 0
                ? receitaMes.divide(BigDecimal.valueOf(pedidosMes), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal receitaAnt  = lancamentoRepository.somarReceitaPorPeriodo(inicioMesAnterior, fimMesAnterior);
        BigDecimal despesasAnt = lancamentoRepository.somarDespesaPorPeriodo(inicioMesAnterior, fimMesAnterior);
        BigDecimal saldoAnt    = receitaAnt.subtract(despesasAnt);
        long pedidosAnt        = pedidoRepository.contarVendasPorPeriodo(StatusPedido.PAGO, inicioMesAnterior, fimMesAnterior);
        BigDecimal ticketAnt   = pedidosAnt > 0
                ? receitaAnt.divide(BigDecimal.valueOf(pedidosAnt), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new KpisResponse(
                receita, despesas, saldo, ticketMedio, totalPedidos,
                variacao(receitaMes, receitaAnt),
                variacao(despesasMes, despesasAnt),
                variacao(saldoMes, saldoAnt),
                variacao(ticketMes, ticketAnt)
        );
    }

    // ── Receita mensal ────────────────────────────────────────────────────────

    public List<ReceitaMensalItemResponse> receitaMensal(int meses) {
        LocalDate hoje  = LocalDate.now();
        LocalDate inicio = hoje.minusMonths(meses).withDayOfMonth(1);

        List<LancamentoFinanceiro> receitas = lancamentoRepository
                .findByTipoAndStatusAndDataPagamentoBetween(TipoLancamento.RECEITA, StatusLancamento.PAGO, inicio, hoje);
        List<LancamentoFinanceiro> despesas = lancamentoRepository
                .findByTipoAndStatusAndDataPagamentoBetween(TipoLancamento.DESPESA, StatusLancamento.PAGO, inicio, hoje);

        Map<YearMonth, BigDecimal> vendasMap = receitas.stream()
                .filter(l -> l.getPedido() != null)
                .collect(Collectors.groupingBy(
                        l -> YearMonth.from(l.getDataPagamento()),
                        Collectors.reducing(BigDecimal.ZERO, LancamentoFinanceiro::getValor, BigDecimal::add)));

        Map<YearMonth, BigDecimal> locacoesMap = receitas.stream()
                .filter(l -> l.getLocacao() != null)
                .collect(Collectors.groupingBy(
                        l -> YearMonth.from(l.getDataPagamento()),
                        Collectors.reducing(BigDecimal.ZERO, LancamentoFinanceiro::getValor, BigDecimal::add)));

        Map<YearMonth, BigDecimal> despesasMap = despesas.stream()
                .collect(Collectors.groupingBy(
                        l -> YearMonth.from(l.getDataPagamento()),
                        Collectors.reducing(BigDecimal.ZERO, LancamentoFinanceiro::getValor, BigDecimal::add)));

        List<ReceitaMensalItemResponse> result = new ArrayList<>();
        YearMonth ym = YearMonth.from(inicio);
        YearMonth fim = YearMonth.from(hoje);
        while (!ym.isAfter(fim)) {
            result.add(new ReceitaMensalItemResponse(
                    ym.toString(),
                    vendasMap.getOrDefault(ym, BigDecimal.ZERO),
                    locacoesMap.getOrDefault(ym, BigDecimal.ZERO),
                    despesasMap.getOrDefault(ym, BigDecimal.ZERO)
            ));
            ym = ym.plusMonths(1);
        }
        return result;
    }

    // ── Status financeiro ─────────────────────────────────────────────────────

    public StatusFinanceiroResponse statusFinanceiro() {
        LocalDate hoje    = LocalDate.now();
        long pagos        = lancamentoRepository.countByStatus(StatusLancamento.PAGO);
        long vencidos     = lancamentoRepository.countByStatusAndDataVencimentoBefore(StatusLancamento.PENDENTE, hoje);
        long totalPend    = lancamentoRepository.countByStatus(StatusLancamento.PENDENTE);
        long pendentes    = totalPend - vencidos;
        return new StatusFinanceiroResponse(pagos, pendentes, vencidos);
    }

    // ── Top clientes ──────────────────────────────────────────────────────────

    public List<TopClienteResponse> topClientes(int limite, String periodo) {
        LocalDate inicio = resolverInicio(periodo, LocalDate.now());
        List<Object[]> rows = lancamentoRepository.topClientesPorReceita(
                inicio, LocalDate.now(), PageRequest.of(0, limite));
        return rows.stream()
                .map(r -> new TopClienteResponse((String) r[0], (BigDecimal) r[1]))
                .toList();
    }

    // ── Top produtos ──────────────────────────────────────────────────────────

    public List<TopProdutoResponse> topProdutos(int limite, String periodo) {
        LocalDate inicio  = resolverInicio(periodo, LocalDate.now());
        LocalDateTime ini = inicio.atStartOfDay();
        LocalDateTime fim = LocalDate.now().atTime(23, 59, 59);
        List<Object[]> rows = itemPedidoRepository.topProdutos(ini, fim, PageRequest.of(0, limite));
        return rows.stream()
                .map(r -> {
                    BigDecimal total = (BigDecimal) r[2];
                    BigDecimal custo = (BigDecimal) r[3];
                    BigDecimal lucro = total.subtract(custo);
                    return new TopProdutoResponse(
                            (String) r[0],
                            ((Number) r[1]).longValue(),
                            lucro,   // r[2] - r[3]
                            custo,
                            total    // r[2]
                    );
                })
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocalDate resolverInicio(String periodo, LocalDate hoje) {
        return switch (periodo != null ? periodo : "6m") {
            case "3m"  -> hoje.minusMonths(3);
            case "12m" -> hoje.minusMonths(12);
            default    -> hoje.minusMonths(6);
        };
    }

    private Double variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior.compareTo(BigDecimal.ZERO) == 0) return null;
        return atual.subtract(anterior)
                .divide(anterior, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
