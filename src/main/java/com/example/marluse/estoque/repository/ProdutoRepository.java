package com.example.marluse.estoque.repository;

import com.example.marluse.estoque.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, String> {

    Optional<Produto> findByNome(String nome);

    List<Produto> findByAtivoTrue();

    List<Produto> findByQuantidadeEstoqueLessThanEqualAndAtivoTrue(Integer quantidade);

    @Query("SELECT p FROM Produto p WHERE p.quantidadeEstoque <= p.estoqueMinimo AND p.ativo = true")
    List<Produto> findEstoqueBaixo();
}
