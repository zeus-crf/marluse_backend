package com.example.marluse.entrega.service;

import com.example.marluse.entrega.dto.EntregaAtualizarRequest;
import com.example.marluse.entrega.dto.EntregaResponse;
import com.example.marluse.entrega.enums.StatusEntrega;
import com.example.marluse.entrega.repository.EntregaRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntregaService {

    private final EntregaRepository entregaRepository;

    @Transactional
    public EntregaResponse entregar(String id) {

        var entrega = entregaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Entrega não encontrada"));
        entrega.setStatus(StatusEntrega.FEITA);

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
        return new EntregaResponse(entrega.getId(), entrega.getEndereco(), entrega.getDataEntrega(), entrega.getDataPrevista(), entrega.getStatus());
    }
}
