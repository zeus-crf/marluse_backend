package com.example.marluse.vendas.service;

import com.example.marluse.clientes.repository.ClienteRepository;
import com.example.marluse.estoque.model.Produto;
import com.example.marluse.estoque.repository.ProdutoRepository;
import com.example.marluse.vendas.dto.ItemPedidoRequest;
import com.example.marluse.vendas.dto.PedidoRequest;
import com.example.marluse.vendas.dto.PedidoResponse;
import com.example.marluse.vendas.enums.StatusPedido;
import com.example.marluse.vendas.model.ItemPedido;
import com.example.marluse.vendas.model.Pedido;
import com.example.marluse.vendas.repository.PedidoRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;

    @Transactional
    public PedidoResponse criar (PedidoRequest request) {

        Pedido pedido = Pedido.builder()
                .status(StatusPedido.PENDENTE)
                .formaPagamento(request.formaPagamento())
                .observacao(request.observacao())
                .valorTotal(BigDecimal.ZERO)
                .build();

        if (request.clienteId() != null){
            pedido.setCliente(clienteRepository.findById(request.clienteId())
                    .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado")));
        }

        BigDecimal total = BigDecimal.ZERO;

        for (ItemPedidoRequest itemRequest : request.itens()){
            Produto produto = produtoRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));

            if (produto.getQuantidadeEstoque() < itemRequest.quantidade()){
                throw new IllegalArgumentException("Estoque insuficiente: " + produto.getNome());
            }

            BigDecimal subTotal = produto.getPreco().multiply(BigDecimal.valueOf(itemRequest.quantidade()));

            ItemPedido item = ItemPedido.builder()
                    .pedido(pedido)
                    .produto(produto)
                    .quantidade(itemRequest.quantidade())
                    .precoUnitario(produto.getPreco())
                    .subTotal(subTotal)
                    .build();

            pedido.getItens().add(item);
            total = total.add(subTotal);

            produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - itemRequest.quantidade());
            produtoRepository.save(produto);
        }

        pedido.setValorTotal(total);
        return PedidoResponse.from(pedidoRepository.save(pedido));
    }

    public List<PedidoResponse> listar(){
        return pedidoRepository.findAll()
                .stream()
                .map(PedidoResponse::from)
                .toList();
    }

    public PedidoResponse buscarPorId(String id){
        return pedidoRepository.findById(id)
                .map(PedidoResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
    }

    public List<PedidoResponse> listarPorStatus(StatusPedido status) {
        return pedidoRepository.findByStatus(status)
                .stream()
                .map(PedidoResponse::from)
                .toList();
    }

    @Transactional
    public PedidoResponse cancelar(String id){
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        if (pedido.getStatus() == StatusPedido.CANCELADO){
            throw new IllegalArgumentException("O pedido já está cancelado");
        }

        for (ItemPedido item : pedido.getItens()){
            Produto produto = item.getProduto();
            produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() + item.getQuantidade());
            produtoRepository.save(produto);
        }

        pedido.setStatus(StatusPedido.CANCELADO);
        return PedidoResponse.from(pedidoRepository.save(pedido));
    }
}
