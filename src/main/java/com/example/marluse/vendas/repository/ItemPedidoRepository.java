package com.example.marluse.vendas.repository;

import com.example.marluse.vendas.model.ItemPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ItemPedidoRepository extends JpaRepository<ItemPedido, String> {

    @Query("""
        SELECT i.produto.nome,
               SUM(i.quantidade),
               SUM(i.subTotal),
               SUM(CASE WHEN i.produto.valorCompra IS NOT NULL
                        THEN i.produto.valorCompra * i.quantidade ELSE 0 END),
               SUM(CASE WHEN i.produto.valorCompra IS NOT NULL
                        THEN i.subTotal - i.produto.valorCompra * i.quantidade
                        ELSE i.subTotal END)
        FROM ItemPedido i
        WHERE i.pedido.status IN ('CONFIRMADO', 'PAGO')
        AND i.pedido.dataMovimento BETWEEN :inicio AND :fim
        GROUP BY i.produto.id, i.produto.nome
        ORDER BY SUM(i.subTotal) DESC
    """)
    List<Object[]> topProdutos(@Param("inicio") LocalDate inicio,
                               @Param("fim") LocalDate fim);

    @Query("""
        SELECT COALESCE(SUM(COALESCE(i.produto.valorCompra, 0) * i.quantidade), 0)
        FROM ItemPedido i
        WHERE i.pedido.status IN ('CONFIRMADO', 'PAGO')
        AND i.pedido.dataMovimento BETWEEN :inicio AND :fim
    """)
    BigDecimal somarCmvPorPeriodo(@Param("inicio") LocalDate inicio,
                                  @Param("fim") LocalDate fim);

    @Query("""
        SELECT i.pedido.dataMovimento, COALESCE(SUM(COALESCE(i.produto.valorCompra, 0) * i.quantidade), 0)
        FROM ItemPedido i
        WHERE i.pedido.status IN ('CONFIRMADO', 'PAGO')
        AND i.pedido.dataMovimento BETWEEN :inicio AND :fim
        GROUP BY i.pedido.dataMovimento
    """)
    List<Object[]> somarCmvPorDia(@Param("inicio") LocalDate inicio,
                                  @Param("fim") LocalDate fim);
}
