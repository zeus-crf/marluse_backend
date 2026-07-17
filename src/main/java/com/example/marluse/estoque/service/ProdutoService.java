package com.example.marluse.estoque.service;

import com.example.marluse.estoque.dto.CategoriaProduto;
import com.example.marluse.estoque.dto.ProdutoAtualizarRequest;
import com.example.marluse.estoque.dto.ProdutoRequest;
import com.example.marluse.estoque.dto.ProdutoResponse;
import com.example.marluse.estoque.enums.UnidadeMedida;
import com.example.marluse.estoque.model.Produto;
import com.example.marluse.estoque.repository.ProdutoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
                .precoDiaria(request.precoDiaria() != null ? request.precoDiaria() : request.preco())
                .valorCompra(request.valorCompra())
                .quantidadeEstoque(request.quantidadeEstoque() != null ? request.quantidadeEstoque() : 0)
                .estoqueMinimo(request.estoqueMinimo() != null ? request.estoqueMinimo() : 0)
                .ativo(true)
                .medida(request.medida())
                .categoria(request.categoria() != null ? request.categoria() : CategoriaProduto.OUTROS)
                .build();

        return ProdutoResponse.from(produtoRepository.save(produto));

    }

    public Produto criarRascunho( String nome, BigDecimal preco, BigDecimal precoDiaria){

        BigDecimal precoVenda = preco != null ? preco :
                BigDecimal.ZERO;

        Produto produto = Produto.builder()
                .nome(nome)
                .preco(preco)
                .precoDiaria(precoDiaria != null ? precoDiaria : precoVenda)
                .valorCompra(BigDecimal.ZERO)
                .quantidadeEstoque(0)
                .estoqueMinimo(0)
                .ativo(true)
                .rascunho(true)
                .medida(UnidadeMedida.PECA)
                .categoria(CategoriaProduto.OUTROS)
                .build();

        return produtoRepository.save(produto);


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

    public List<ProdutoResponse> listarRascunho() {
        return produtoRepository.findByAtivoAndRascunhoTrue()
                .stream()
                .map(ProdutoResponse::from)
                .toList();
    }

    public ProdutoResponse atualizar(String id, ProdutoAtualizarRequest request){
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));

        if (request.nome() != null) produto.setNome(request.nome());
        if (request.descricao() != null) produto.setDescricao(request.descricao());
        if (request.valorCompra() != null) produto.setValorCompra(request.valorCompra());
        if (request.preco() != null) produto.setPreco(request.preco());
        if (request.precoDiaria() != null) produto.setPrecoDiaria(request.precoDiaria());
        if (request.medida() != null) produto.setMedida(request.medida());
        if (request.quantidadeEstoque() != null) produto.setQuantidadeEstoque(request.quantidadeEstoque());
        if (request.categoria() != null ) produto.setCategoria(request.categoria());

        produto.setEstoqueMinimo(request.estoqueMinimo() != null ? request.estoqueMinimo() : 0);

        produto.setRascunho(false);

        return ProdutoResponse.from(produtoRepository.save(produto));
    }

    public List<ProdutoResponse> listarEstoqueBaixo() {
        return produtoRepository.findEstoqueBaixo()
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
