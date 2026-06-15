package com.example.marluse.security;

import com.example.marluse.security.dto.AuthRequest;
import com.example.marluse.security.dto.AuthResponse;
import com.example.marluse.security.dto.RegisterRequest;
import com.example.marluse.security.service.AuthService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AuthServiceTest {

    @Autowired
    private AuthService authService;


    @Test
    void deveRegistrarUsuarioComSucesso(){
        RegisterRequest request = new RegisterRequest("João", "joao@test.com", "senha123");
        AuthResponse response = authService.register(request);

        assertNotNull(response.token());
        assertEquals("joao@test.com", response.email());
        assertEquals("João", response.nome());
    }

    @Test
    void deveLancarExcecaoAoRegistrarEmailDuplicado(){
        RegisterRequest request = new RegisterRequest("João", "joao@test.com", "senha123");
        AuthResponse response = authService.register(request);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }

    @Test
    void deveRealizarLoginComSucesso(){
        RegisterRequest request = new RegisterRequest("João", "joao@test.com", "senha123");
        AuthResponse response = authService.register(request);

        AuthRequest loginRequest = new AuthRequest("joao@test.com", "senha123");
        AuthResponse response2 = authService.login(loginRequest);

        assertNotNull(response.token());
        assertEquals("joao@test.com", response2.email());

    }

    @Test
    void deveLancarExcecaoAoFazerLoginComSenhaErrada(){
        RegisterRequest request = new RegisterRequest("João", "joao@test.com", "senha123");
        AuthResponse response = authService.register(request);

        AuthRequest authRequest = new AuthRequest("joao@test.com", "senha1234");

        assertThrows(Exception.class, () -> authService.login(authRequest));
    }
}
