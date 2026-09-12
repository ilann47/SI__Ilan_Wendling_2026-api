package com.kaneko.estacionamento.usuario.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Dados publicos aceitos no autocadastro de uma identidade global. */
public final class CadastroContaRequest {

    @NotBlank(message = "O nome é obrigatório.")
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
    @Schema(example = "Maria da Silva")
    private String nome;

    @NotBlank(message = "O login é obrigatório.")
    @Size(min = 3, max = 60, message = "O login deve ter entre 3 e 60 caracteres.")
    @Schema(example = "maria.silva")
    private String login;

    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "E-mail inválido.")
    @Size(max = 120, message = "O e-mail deve ter no máximo 120 caracteres.")
    @Schema(example = "maria.silva@example.com")
    private String email;

    @NotBlank(message = "A senha é obrigatória.")
    @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
    @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String senha;

    private final Map<String, Object> camposExtras = new HashMap<>();

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome != null ? nome.trim() : null;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = normalizarIdentificador(login);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalizarIdentificador(email);
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    @JsonAnySetter
    public void receberCampoDesconhecido(String nomeCampo, Object valor) {
        camposExtras.put(nomeCampo, valor);
    }

    @AssertTrue(message = "O cadastro contém campos não permitidos.")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isSemCamposExtras() {
        return camposExtras.isEmpty();
    }

    private static String normalizarIdentificador(String valor) {
        return valor != null ? valor.trim().toLowerCase(Locale.ROOT) : null;
    }
}
