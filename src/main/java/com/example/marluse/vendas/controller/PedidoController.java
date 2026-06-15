package com.example.marluse.vendas.controller;

import com.example.marluse.estoque.service.ProdutoService;
import com.example.marluse.shared.ApiResponse;
import com.example.marluse.vendas.dto.PedidoRequest;
import com.example.marluse.vendas.dto.PedidoResponse;
import com.example.marluse.vendas.enums.StatusPedido;
import com.example.marluse.vendas.model.Pedido;
import com.example.marluse.vendas.service.PedidoService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;


    @PostMapping
    public ResponseEntity<ApiResponse<PedidoResponse>> criar(@Valid @RequestBody PedidoRequest request){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Pedido criado com sucesso!", pedidoService.criar(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PedidoResponse>>> listar(){
        return ResponseEntity.ok(ApiResponse.ok(pedidoService.listar()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PedidoResponse>> listarPorId(String id){
        return ResponseEntity.ok(ApiResponse.ok(pedidoService.buscarPorId(id)));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<PedidoResponse>>> listarPorStatus(@PathVariable StatusPedido status) {
        return ResponseEntity.ok(ApiResponse.ok(pedidoService.listarPorStatus(status)));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<ApiResponse<PedidoResponse>> cancelar(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok("Pedido cancelado", pedidoService.cancelar(id)));
    }

}
