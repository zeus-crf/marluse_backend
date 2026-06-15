package com.example.marluse.vendas;

import com.example.marluse.clientes.model.Cliente;
import com.example.marluse.clientes.repository.ClienteRepository;
import com.example.marluse.estoque.model.Produto;
import com.example.marluse.estoque.repository.ProdutoRepository;
import com.example.marluse.estoque.enums.UnidadeMedida;
import com.example.marluse.vendas.dto.ItemPedidoRequest;
import com.example.marluse.vendas.dto.PedidoRequest;
import com.example.marluse.vendas.dto.PedidoResponse;
import com.example.marluse.vendas.enums.FormaPagamento;
import com.example.marluse.vendas.enums.StatusPedido;
import com.example.marluse.vendas.repository.PedidoRepository;
import com.example.marluse.vendas.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PedidoServiceTest {

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    private Produto produto;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        pedidoRepository.deleteAll();
        produtoRepository.deleteAll();
        clienteRepository.deleteAll();

        produto = produtoRepository.save(Produto.builder()
                .nome("Cimento")
                .preco(new BigDecimal("35.90"))
                .quantidadeEstoque(50)
                .estoqueMinimo(5)
                .medida(UnidadeMedida.SACO)
                .ativo(true)
                .build());

        cliente = clienteRepository.save(Cliente.builder()
                .nome("João Silva")
                .cpfCnpj("123.456.789-00")
                .ativo(true)
                .consumidorFinal(false)
                .build());
    }

    @Test
    void deveCriarPedidoComSucesso() {
        PedidoRequest request = new PedidoRequest(
                cliente.getId(),
                FormaPagamento.PIX,
                List.of(new ItemPedidoRequest(produto.getId(), 3)),
                null
        );

        PedidoResponse response = pedidoService.criar(request);

        assertNotNull(response.id());
        assertEquals(StatusPedido.PENDENTE, response.status());
        assertEquals(new BigDecimal("107.70"), response.valorTotal());
        assertEquals(1, response.itens().size());
    }

    @Test
    void deveDiminuirEstoqueAoCriarPedido() {
        PedidoRequest request = new PedidoRequest(
                null,
                FormaPagamento.DINHEIRO,
                List.of(new ItemPedidoRequest(produto.getId(), 10)),
                null
        );

        pedidoService.criar(request);

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(40, atualizado.getQuantidadeEstoque());
    }

    @Test
    void deveLancarExcecaoQuandoEstoqueInsuficiente() {
        PedidoRequest request = new PedidoRequest(
                null,
                FormaPagamento.PIX,
                List.of(new ItemPedidoRequest(produto.getId(), 100)),
                null
        );

        assertThrows(IllegalArgumentException.class, () -> pedidoService.criar(request));
    }

    @Test
    void deveCancelarPedidoERestaurarEstoque() {
        PedidoRequest request = new PedidoRequest(
                null,
                FormaPagamento.PIX,
                List.of(new ItemPedidoRequest(produto.getId(), 5)),
                null
        );

        PedidoResponse pedido = pedidoService.criar(request);
        pedidoService.cancelar(pedido.id());

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertEquals(50, atualizado.getQuantidadeEstoque());
    }

    @Test
    void deveCriarPedidoSemCliente() {
        PedidoRequest request = new PedidoRequest(
                null,
                FormaPagamento.DINHEIRO,
                List.of(new ItemPedidoRequest(produto.getId(), 1)),
                "Venda balcão"
        );

        PedidoResponse response = pedidoService.criar(request);

        assertNull(response.clienteId());
        assertEquals("Consumidor Final", response.clienteNome());
    }
}