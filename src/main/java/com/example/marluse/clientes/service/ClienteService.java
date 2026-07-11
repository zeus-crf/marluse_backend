package com.example.marluse.clientes.service;

import com.example.marluse.clientes.dto.*;
import com.example.marluse.clientes.model.Cliente;
import com.example.marluse.clientes.repository.ClienteRepository;
import com.example.marluse.locacoes.repository.LocacaoRepository;
import com.example.marluse.vendas.repository.PedidoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final LocacaoRepository locacaoRepository;

    public ClienteResponse criar(ClienteRequest request) {
        if (request.cpfCnpj() != null && clienteRepository.existsByCpfCnpj(request.cpfCnpj())) {
            throw new IllegalArgumentException("CPF/CNPJ já cadastrado");
        }

        Cliente cliente = Cliente.builder()
                .nome(request.nome())
                .cpfCnpj(request.cpfCnpj())
                .telefone(request.telefone())
                .email(request.email())
                .endereco(request.endereco())
                .consumidorFinal(request.consumidorFinal())
                .ativo(true)
                .build();

        return ClienteResponse.from(clienteRepository.save(cliente));
    }

    public List<ClienteResponse> listar() {
        Map<String, BigDecimal> totalPedidos = pedidoRepository.somarPorTodosClientes()
                .stream().collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (BigDecimal) row[1]
                ));

        Map<String, BigDecimal> totalLocacoes = locacaoRepository.somarPorTodosClientes()
                .stream().collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (BigDecimal) row[1]
                ));

        return clienteRepository.findAll().stream().map(c -> {
            BigDecimal gasto = totalPedidos.getOrDefault(c.getId(), BigDecimal.ZERO)
                    .add(totalLocacoes.getOrDefault(c.getId(), BigDecimal.ZERO));
            return ClienteResponse.from(c, gasto);
        }).toList();
    }

    public ClienteResponse listarPorId(String id) {
        return clienteRepository.findById(id)
                .map(ClienteResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado"));
    }


    public ClienteHistoricoResponse historicoCliente(String id){
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado"));

        List<PedidoResumo> pedidos = pedidoRepository
                .findByClienteId(cliente.getId())
                .stream()
                .map(p -> new PedidoResumo(
                        p.getId(),
                        p.getNumero(),
                        p.getStatus().name(),
                        p.getFormaPagamento().name(),
                        p.getValorTotal(),
                        p.getDataMovimento()
                ))
                .toList();

        List<LocacaoResumo> locacoes = locacaoRepository
                .findByClienteId(cliente.getId())
                .stream()
                .map(l -> new LocacaoResumo(
                        l.getId(),
                        l.getNumero(),
                        l.getStatus().name(),
                        l.getFormaPagamento().name(),
                        l.getValorTotal(),
                        l.getDataRetirada(),
                        l.getDataDevolucaoPrevista()
                ))
                .toList();

        return new ClienteHistoricoResponse(pedidos, locacoes);
    }

    public ClienteResponse atualizar(String id, ClienteAtualizarRequest request) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado"));

        if (request.nome()     != null) cliente.setNome(request.nome());
        if (request.cpfCnpj()  != null) cliente.setCpfCnpj(request.cpfCnpj());
        if (request.telefone() != null) cliente.setTelefone(request.telefone());
        if (request.email()    != null) cliente.setEmail(request.email());
        if (request.endereco() != null) cliente.setEndereco(request.endereco());

        return ClienteResponse.from(clienteRepository.save(cliente));
    }

    public void inativar(String id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado"));
        cliente.setAtivo(false);
        clienteRepository.save(cliente);
    }
}
