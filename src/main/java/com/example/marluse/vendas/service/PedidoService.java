package com.example.marluse.vendas.service;

import com.example.marluse.clientes.model.Cliente;
import com.example.marluse.clientes.repository.ClienteRepository;
import com.example.marluse.estoque.model.Produto;
import com.example.marluse.estoque.repository.ProdutoRepository;
import com.example.marluse.financeiro.enums.StatusLancamento;
import com.example.marluse.financeiro.repository.LancamentoFinanceiroRepository;
import com.example.marluse.financeiro.service.LancamentoFinanceiroService;
import com.example.marluse.locacoes.service.LocacaoService;
import com.example.marluse.vendas.dto.ItemPedidoRequest;
import com.example.marluse.vendas.dto.ItemPedidoResponse;
import com.example.marluse.vendas.dto.PedidoRequest;
import com.example.marluse.vendas.dto.PedidoResponse;
import com.example.marluse.vendas.enums.FormaPagamento;
import com.example.marluse.vendas.enums.StatusPedido;
import com.example.marluse.vendas.model.ItemPedido;
import com.example.marluse.vendas.model.Pedido;
import com.example.marluse.vendas.repository.PedidoRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;

    private final LancamentoFinanceiroService lancamentoService;
    private final LancamentoFinanceiroRepository lancamentoRepository;
    @Transactional
    public PedidoResponse criar (PedidoRequest request) {

        Cliente cliente = null;
        if (request.clienteId() != null) {
            cliente = clienteRepository.findById(request.clienteId())
                    .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado"));
        }


        Pedido pedido = Pedido.builder()
                .status(StatusPedido.CONFIRMADO)
                .formaPagamento(request.formaPagamento())
                .observacao(request.observacao())
                .valorTotal(BigDecimal.ZERO)
                .build();

        if (request.clienteId() != null) {
            pedido.setCliente(clienteRepository.findById(request.clienteId())
                    .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado")));
        }

        BigDecimal total = BigDecimal.ZERO;

        for (ItemPedidoRequest itemRequest : request.itens()) {
            Produto produto = produtoRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));

            if (produto.getQuantidadeEstoque() < itemRequest.quantidade()) {
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
        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        String nomeCliente = cliente != null ? cliente.getNome() : "Consumidor Final";

        if (request.formaPagamento() == FormaPagamento.FIADO) {
            lancamentoService.registrarVendaReceita(
                    pedidoSalvo,
                    "Venda fiado - " + nomeCliente,
                    total,
                    StatusLancamento.PENDENTE,
                    LocalDate.now().plusDays(30)
            );
        } else {
            lancamentoService.registrarVendaReceita(
                    pedidoSalvo,
                    "Venda - " + nomeCliente,
                    total,
                    StatusLancamento.PAGO,
                    LocalDate.now()
            );
        }
        return toResponse(pedidoSalvo);
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
    public PedidoResponse pagar(String id) {
        Pedido pedido = buscarEntidade(id);

        if (pedido.getFormaPagamento() != FormaPagamento.FIADO) {
            throw new IllegalArgumentException("Pedido não é FIADO, não requer pagamento posterior");
        }

        lancamentoRepository.findByPedidoId(id)
                .ifPresent(l -> lancamentoService.pagar(l.getId()));

        return toResponse(pedido);
    }

    @Transactional
    public PedidoResponse cancelar(String id) {
        Pedido pedido = buscarEntidade(id);

        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new IllegalArgumentException("Pedido já está cancelado");
        }

        for (ItemPedido item : pedido.getItens()) {
            Produto produto = item.getProduto();
            produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() + item.getQuantidade());
            produtoRepository.save(produto);
        }

        pedido.setStatus(StatusPedido.CANCELADO);
        return toResponse(pedidoRepository.save(pedido));
    }

    private PedidoResponse toResponse (Pedido pedido){

        List<ItemPedidoResponse> itens = pedido.getItens().stream()
                .map(item -> new ItemPedidoResponse(
                        item.getId(),
                        item.getProduto().getId(),
                        item.getProduto().getNome(),
                        item.getQuantidade(),
                        item.getPrecoUnitario(),
                        item.getSubTotal()
                ))
                .toList();
        return new PedidoResponse(
                pedido.getId(),
                pedido.getCliente() != null ? pedido.getCliente().getId() : null,
                pedido.getCliente() != null ? pedido.getCliente().getNome() : "Consumidor Final",
                pedido.getStatus(),
                pedido.getFormaPagamento(),
                pedido.getValorTotal(),
                pedido.getObservacao(),
                itens,
                pedido.getCreatedAt()
        );
    }
    private Pedido buscarEntidade(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado: " + id));
    }
}
