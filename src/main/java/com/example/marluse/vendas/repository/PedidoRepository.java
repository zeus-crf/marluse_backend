package com.example.marluse.vendas.repository;

import com.example.marluse.vendas.enums.StatusPedido;
import com.example.marluse.vendas.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, String> {
    List<Pedido> findByStatus(StatusPedido status);

    List<Pedido> findByClienteId(String clienteId);
}
