package com.example.marluse.clientes.service;

import com.example.marluse.clientes.dto.ClienteAtualizarRequest;
import com.example.marluse.clientes.dto.ClienteRequest;
import com.example.marluse.clientes.dto.ClienteResponse;
import com.example.marluse.clientes.model.Cliente;
import com.example.marluse.clientes.repository.ClienteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteResponse criar(ClienteRequest request){
        if ( request.cpfCnpj() != null && clienteRepository.existsByCpfCnpj(request.cpfCnpj())){
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

    public List<ClienteResponse> listar(){
        return clienteRepository.findAll()
                .stream()
                .map(ClienteResponse::from)
                .toList();
    }

    public ClienteResponse listarPorId(String id){
        return clienteRepository.findById(id)
                .map(ClienteResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado"));


    }

    public ClienteResponse atualizar(String id, ClienteAtualizarRequest request) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado"));

        if (request.nome() != null ) cliente.setNome(request.nome());
        if (request.cpfCnpj() != null ) cliente.setCpfCnpj(request.cpfCnpj());
        if (request.telefone() != null ) cliente.setTelefone(request.telefone());
        if (request.email() != null ) cliente.setEmail(request.email());
        if (request.endereco() != null ) cliente.setEndereco(request.endereco());

        return ClienteResponse.from(clienteRepository.save(cliente));
    }

    public void inativar(String id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado"));
        cliente.setAtivo(false);
        clienteRepository.save(cliente);
    }
}
