package com.example.marluse.security.dto;

import lombok.Builder;

@Builder
public record AuthResponse (
        String token,
        String email,
        String nome
){
}
