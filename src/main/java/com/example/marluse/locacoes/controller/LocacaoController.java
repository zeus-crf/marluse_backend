package com.example.marluse.locacoes.controller;

import com.example.marluse.locacoes.dto.LocacaoEdicaoRequest;
import com.example.marluse.locacoes.dto.LocacaoRequest;
import com.example.marluse.locacoes.dto.LocacaoResponse;
import com.example.marluse.locacoes.enums.StatusLocacao;
import com.example.marluse.locacoes.scheduler.LocacaoScheduler;
import com.example.marluse.locacoes.service.LocacaoService;
import com.example.marluse.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locacoes")
@RequiredArgsConstructor
public class LocacaoController {

    private final LocacaoService locacaoService;
    private final LocacaoScheduler locacaoScheduler;

    @PostMapping
    public ResponseEntity<ApiResponse<LocacaoResponse>> criar(
            @Valid @RequestBody LocacaoRequest request,
            @RequestParam(defaultValue = "false") boolean isOrcamento) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Locação criada com sucesso", locacaoService.criar(request, isOrcamento)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LocacaoResponse>>> listarTodas() {
        return ResponseEntity.ok(ApiResponse.ok(locacaoService.listarTodas()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LocacaoResponse>> buscarPorId(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(locacaoService.listarPorId(id)));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<LocacaoResponse>>> listarPorStatus(@PathVariable StatusLocacao status) {
        return ResponseEntity.ok(ApiResponse.ok(locacaoService.listarPorStatus(status)));
    }

    @GetMapping("/atrasadas")
    public ResponseEntity<ApiResponse<List<LocacaoResponse>>> listarAtrasadas() {
        return ResponseEntity.ok(ApiResponse.ok(locacaoService.listarAtrasadas()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<LocacaoResponse>> editar(
            @PathVariable String id,
            @Valid @RequestBody LocacaoEdicaoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Locação atualizada", locacaoService.editar(id, request)));
    }

    @PatchMapping("/{id}/devolver")
    public ResponseEntity<ApiResponse<LocacaoResponse>> devolver(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok("Locação devolvida com sucesso", locacaoService.devolver(id)));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<ApiResponse<LocacaoResponse>> cancelar(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok("Locação cancelada", locacaoService.cancelar(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        locacaoService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    /** Endpoint temporário para testar o scheduler manualmente */
    @PostMapping("/dev/marcar-atrasadas")
    public ResponseEntity<String> testarScheduler() {
        locacaoScheduler.marcarLocacoesAtrasadas();
        return ResponseEntity.ok("Scheduler executado — verifique os logs e o banco.");
    }
}