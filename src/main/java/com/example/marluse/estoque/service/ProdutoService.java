package com.example.marluse.estoque.service;

import com.example.marluse.estoque.dto.ProdutoAtualizarRequest;
import com.example.marluse.estoque.dto.ProdutoRequest;
import com.example.marluse.estoque.dto.ProdutoResponse;
import com.example.marluse.estoque.model.Produto;
import com.example.marluse.estoque.repository.ProdutoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;


    public ProdutoResponse criar(ProdutoRequest request){
        if (produtoRepository.findByNome(request.nome()).isPresent()){
            throw new IllegalArgumentException("Esse produto já exite");
        }

        Produto produto = Produto.builder()
                .nome(request.nome())
                .descricao(request.descricao())
                .preco(request.preco())
                .quantidadeEstoque(request.quantidadeEstoque() != null ? request.quantidadeEstoque() : 0)
                .ativo(true)
                .medida(request.medida())
                .build();

        return ProdutoResponse.from(produtoRepository.save(produto));

    }

    public List<ProdutoResponse> listar(){
        return produtoRepository.findByAtivoTrue()
                .stream()
                .map(ProdutoResponse::from)
                .toList();
    }

    public ProdutoResponse burcarPorId(String id){
        return produtoRepository.findById(id)
                .map(ProdutoResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));
    }

    public ProdutoResponse atualizar(String id, ProdutoAtualizarRequest request){
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));

        if (request.nome() != null) produto.setNome(request.nome());
        if (request.descricao() != null) produto.setDescricao(request.descricao());
        if (request.preco() != null) produto.setPreco(request.preco());
        if (request.medida() != null) produto.setMedida(request.medida());
        produto.setEstoqueMinimo(request.estoqueMinimo() != null ? request.estoqueMinimo() : 0);

        return ProdutoResponse.from(produtoRepository.save(produto));
    }

    public List<ProdutoResponse> listarEstoqueBaixo(){
        return produtoRepository.findByQuantidadeEstoqueLessThanEqualAndAtivoTrue(0)
                .stream()
                .map(ProdutoResponse::from)
                .toList();
    }

    public void inativar(String id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));
        produto.setAtivo(false);
        produtoRepository.save(produto);
    }
}
