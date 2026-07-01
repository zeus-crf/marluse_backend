package com.example.marluse.vendas.repository;

import com.example.marluse.vendas.model.ItemPedido;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ItemPedidoRepository extends JpaRepository<ItemPedido, String> {

    @Query("""
        SELECT i.produto.nome, SUM(i.quantidade), SUM(i.subTotal)
        FROM ItemPedido i
        WHERE i.pedido.status IN ('CONFIRMADO', 'PAGO')
        AND i.pedido.createdAt BETWEEN :inicio AND :fim
        GROUP BY i.produto.id, i.produto.nome
        ORDER BY SUM(i.quantidade) DESC
    """)
    List<Object[]> topProdutos(@Param("inicio") LocalDateTime inicio,
                               @Param("fim") LocalDateTime fim,
                               Pageable pageable);
}
