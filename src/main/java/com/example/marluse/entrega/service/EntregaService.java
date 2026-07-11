package com.example.marluse.entrega.service;

import com.example.marluse.entrega.dto.EntregaAtualizarRequest;
import com.example.marluse.entrega.dto.EntregaResponse;
import com.example.marluse.entrega.enums.StatusEntrega;
import com.example.marluse.entrega.repository.EntregaRepository;
import com.example.marluse.estoque.model.Produto;
import com.example.marluse.estoque.repository.ProdutoRepository;
import com.example.marluse.locacoes.model.ItemLocacao;
import com.example.marluse.locacoes.model.Locacao;
import com.example.marluse.locacoes.repository.LocacaoRepository;
import com.example.marluse.vendas.model.ItemPedido;
import com.example.marluse.vendas.model.Pedido;
import com.example.marluse.vendas.repository.PedidoRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class EntregaService {

    private final EntregaRepository entregaRepository;
    private final ProdutoRepository produtoRepository;
    private final PedidoRepository pedidoRepository;
    private final LocacaoRepository locacaoRepository;

    @Transactional
    public EntregaResponse entregar(String id) {

        var entrega = entregaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Entrega não encontrada"));

        // Se a entrega está vinculada a um pedido cujo estoque ainda não foi descontado,
        // desconta agora (pedidos com entrega só baixam estoque na confirmação da entrega)
        Pedido pedido = entrega.getPedido();
        if (pedido != null && !pedido.isEstoqueDescontado()) {
            for (ItemPedido item : pedido.getItens()) {
                Produto produto = item.getProduto();
                if (produto.getQuantidadeEstoque() < item.getQuantidade()) {
                    throw new IllegalArgumentException(
                            "Estoque insuficiente para entregar: " + produto.getNome());
                }
                produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - item.getQuantidade());
                produtoRepository.save(produto);
            }
            pedido.setEstoqueDescontado(true);
            pedidoRepository.save(pedido);
        }

        // Desconta estoque da locação vinculada (se ainda não foi descontado)
        Locacao locacao = entrega.getLocacao();
        if (locacao != null && !locacao.isEstoqueDescontado()) {
            for (ItemLocacao item : locacao.getItens()) {
                Produto produto = item.getProduto();
                if (produto.getQuantidadeEstoque() < item.getQuantidade()) {
                    throw new IllegalArgumentException(
                            "Estoque insuficiente para entregar: " + produto.getNome());
                }
                produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - item.getQuantidade());
                produtoRepository.save(produto);
            }
            locacao.setEstoqueDescontado(true);
            locacaoRepository.save(locacao);
        }

        entrega.setStatus(StatusEntrega.FEITA);
        entrega.setDataEntrega(LocalDate.now());

        entregaRepository.save(entrega);
        return new EntregaResponse(entrega.getId(), entrega.getEndereco(), entrega.getDataPrevista(), entrega.getDataEntrega(), entrega.getStatus());
    }

    @Transactional
    public EntregaResponse editar(String id, EntregaAtualizarRequest request) {

        var entrega = entregaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Entrega não encontrada"));

        if (request.endereco() != null) entrega.setEndereco(request.endereco());
        if (request.dataPrevista() != null) entrega.setDataPrevista(request.dataPrevista());

        entregaRepository.save(entrega);
        return new EntregaResponse(entrega.getId(), entrega.getEndereco(), entrega.getDataPrevista(), entrega.getDataEntrega(), entrega.getStatus());
    }
}
