package com.example.marluse.security.controller;

import com.example.marluse.security.dto.AuthRequest;
import com.example.marluse.security.dto.AuthResponse;
import com.example.marluse.security.dto.RegisterRequest;
import com.example.marluse.security.service.AuthService;
import com.example.marluse.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthContoller {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request){
        return ResponseEntity.ok(ApiResponse.ok("Login realizado com sucesso", authService.login(request)));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Usuário criado com secesso", authService.register(request)));
    }
}
