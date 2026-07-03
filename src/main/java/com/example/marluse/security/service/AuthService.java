package com.example.marluse.security.service;

import com.example.marluse.security.model.Usuario;
import com.example.marluse.security.repository.UsuarioRepository;
import com.example.marluse.security.dto.AuthRequest;
import com.example.marluse.security.dto.AuthResponse;
import com.example.marluse.security.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;



    public AuthResponse login(@Valid AuthRequest request){
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        Usuario usuario = (Usuario) userDetailsService.loadUserByUsername(request.email());

        return AuthResponse.builder()
                .token(jwtService.generateAccessToken(usuario))
                .email(usuario.getEmail())
                .nome(usuario.getNome())
                .build();
    }

    public AuthResponse register(@Valid @RequestBody RegisterRequest request){

        if (usuarioRepository.findByEmail(request.email()).isPresent()){
            throw new IllegalArgumentException("Esse email já está cadastrado, logue com ele");
        }

        Usuario usuario = Usuario.builder()
                .nome(request.nome())
                .email(request.email())
                .senha(passwordEncoder.encode(request.password()))
                .ativo(true)
                .build();

        usuarioRepository.save(usuario);

        return AuthResponse.builder()
                .token(jwtService.generateAccessToken(usuario))
                .email(usuario.getEmail())
                .nome(usuario.getNome())
                .build();

    }
}
