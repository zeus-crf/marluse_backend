package com.example.marluse.clientes.dto;

import jakarta.validation.constraints.NotBlank;

public record ClienteAtualizarRequest(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        String cpfCnpj,
        String telefone,
        String email,
        String endereco
) {
}
