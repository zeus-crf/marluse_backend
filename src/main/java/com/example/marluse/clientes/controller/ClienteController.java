package com.example.marluse.clientes.controller;

import com.example.marluse.clientes.dto.ClienteAtualizarRequest;
import com.example.marluse.clientes.dto.ClienteRequest;
import com.example.marluse.clientes.dto.ClienteResponse;
import com.example.marluse.clientes.service.ClienteService;
import com.example.marluse.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.SimpleTimeZone;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @PostMapping
    public ResponseEntity<ApiResponse<ClienteResponse>> criar(@Valid @RequestBody ClienteRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Cliente criado com sucesso!", clienteService.criar(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ClienteResponse>>> listar(){
        return ResponseEntity.ok(ApiResponse.ok(clienteService.listar()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClienteResponse>> listarPorId(@PathVariable String id){
        return ResponseEntity.ok(ApiResponse.ok(clienteService.listarPorId(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ClienteResponse>> atualizar(
            @PathVariable String id,
            @Valid @RequestBody ClienteAtualizarRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cliente atualizado com sucesso", clienteService.atualizar(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> inativar(@PathVariable String id){
        clienteService.inativar(id);
        return ResponseEntity.ok(ApiResponse.ok("Cliente inativado com sucesso", null));
    }


}
