package com.example.marluse.security.model;

import com.example.marluse.shared.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken extends BaseEntity {

    private String token;

    @ManyToOne
    private Usuario usuario;

    private LocalDateTime expiresAt;

    private boolean revogado;
}
