package com.example.marluse.financeiro.controller;

import com.example.marluse.financeiro.dto.LancamentoFinanceiroRequest;
import com.example.marluse.financeiro.dto.LancamentoFinanceiroResponse;
import com.example.marluse.financeiro.service.LancamentoFinanceiroService;
import com.example.marluse.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/financeiro")
@RequiredArgsConstructor
public class LancamentoFinanceiroController {

    private final LancamentoFinanceiroService lancamentoService;

    @PostMapping
    public ResponseEntity<ApiResponse<LancamentoFinanceiroResponse>> criar(
            @Valid @RequestBody LancamentoFinanceiroRequest request) {
        LancamentoFinanceiroResponse response = lancamentoService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Lançamento criado com sucesso", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LancamentoFinanceiroResponse>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(lancamentoService.listarTodos()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LancamentoFinanceiroResponse>> buscarPorId(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(lancamentoService.listarPorId(id)));
    }

    @GetMapping("/pendentes")
    public ResponseEntity<ApiResponse<List<LancamentoFinanceiroResponse>>> listarPendentes() {
        return ResponseEntity.ok(ApiResponse.ok(lancamentoService.listarPendentes()));
    }

    @GetMapping("/vencidos")
    public ResponseEntity<ApiResponse<List<LancamentoFinanceiroResponse>>> listarVencidos() {
        return ResponseEntity.ok(ApiResponse.ok(lancamentoService.listarVencidos()));
    }

    @PatchMapping("/{id}/pagar")
    public ResponseEntity<ApiResponse<LancamentoFinanceiroResponse>> pagar(@PathVariable String id) {
        LancamentoFinanceiroResponse response = lancamentoService.pagar(id);
        return ResponseEntity.ok(ApiResponse.ok("Lançamento pago com sucesso", response));
    }

    @GetMapping("/resumo-dia")
    public ResponseEntity<ApiResponse<Object>> resumoDia() {
        return ResponseEntity.ok(ApiResponse.ok(lancamentoService.resumoDia()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletar(@PathVariable String id) {
        lancamentoService.deletar(id);
        return ResponseEntity.ok(ApiResponse.ok("Lançamento removido com sucesso", null));
    }
}