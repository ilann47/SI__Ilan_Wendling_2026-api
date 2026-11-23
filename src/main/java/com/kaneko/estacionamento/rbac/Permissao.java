package com.kaneko.estacionamento.rbac;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "permissao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Permissao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 100) private String codigo;
    @Column(nullable = false, length = 160) private String nome;
}
