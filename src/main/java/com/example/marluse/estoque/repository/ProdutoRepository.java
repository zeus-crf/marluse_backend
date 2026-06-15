package com.example.marluse.estoque.repository;

import com.example.marluse.estoque.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, String> {

    Optional<Produto> findByNome(String nome);

    List<Produto> findByAtivoTrue();

    List<Produto> findByQuantidadeEstoqueLessThanEqualAndAtivoTrue(Integer quantidade);

}
