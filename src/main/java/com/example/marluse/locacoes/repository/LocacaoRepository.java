package com.example.marluse.locacoes.repository;

import com.example.marluse.locacoes.enums.StatusLocacao;
import com.example.marluse.locacoes.model.Locacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LocacaoRepository extends JpaRepository<Locacao, String> {

    List<Locacao> findByStatus(StatusLocacao status);

    List<Locacao> findByClienteId(String clienteId);

    List<Locacao> findByStatusAndDataDevolucaoPrevistaBefore(StatusLocacao status, LocalDate data);
}