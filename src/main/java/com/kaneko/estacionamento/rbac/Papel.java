package com.kaneko.estacionamento.rbac;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "papel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Papel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 60) private String codigo;
    @Column(nullable = false, length = 160) private String nome;
    @Column(nullable = false) private Boolean sistema;
}
