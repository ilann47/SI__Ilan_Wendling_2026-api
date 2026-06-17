package com.kaneko.estacionamento.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.UsuarioService;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autorizacao por perfil (segregacao de funcoes) com {@code app.security.enabled=true}.
 *
 * <p>Cobre a falha critica em que um OPERADOR autenticado executava acoes de ADMIN
 * (criar/excluir usuarios — inclusive promovendo a si mesmo — e excluir cadastros).
 * Garante que tais acoes retornam 403 para OPERADOR e seguem permitidas para ADMIN,
 * sem bloquear a operacao do dia a dia do OPERADOR.
 */
@SpringBootTest(properties = "app.security.enabled=true")
@AutoConfigureMockMvc
@Transactional
class AutorizacaoPerfilTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private JwtService jwtService;

    private String tokenAdmin;
    private String tokenOperador;

    @BeforeEach
    void setup() {
        usuarioService.criar(new UsuarioRequest("Administrador", "admin_test", "senha123",
                null, null, PerfilUsuario.ADMIN));
        usuarioService.criar(new UsuarioRequest("Operador", "oper_test", "senha123",
                null, null, PerfilUsuario.OPERADOR));
        tokenAdmin = "Bearer " + jwtService.gerarToken("admin_test", "ADMIN");
        tokenOperador = "Bearer " + jwtService.gerarToken("oper_test", "OPERADOR");
    }

    private static final String NOVO_USUARIO = """
            { "nome": "Novo", "login": "novo_user", "senha": "senha123", "perfil": "OPERADOR" }""";
    private static final String NOVO_ADMIN = """
            { "nome": "Hacker", "login": "hacker", "senha": "senha123", "perfil": "ADMIN" }""";

    // ---- OPERADOR e barrado nas acoes administrativas (a falha critica original) ----

    @Test
    void operadorNaoCriaUsuario() throws Exception {
        mvc.perform(post("/api/usuarios").header("Authorization", tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON).content(NOVO_USUARIO))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorNaoSeAutoPromoveCriandoAdmin() throws Exception {
        mvc.perform(post("/api/usuarios").header("Authorization", tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON).content(NOVO_ADMIN))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorNaoListaUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios").header("Authorization", tokenOperador))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorNaoExcluiUsuario() throws Exception {
        mvc.perform(delete("/api/usuarios/1").header("Authorization", tokenOperador))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorNaoExcluiCliente() throws Exception {
        // A autorizacao e avaliada antes do handler: 403 mesmo para id inexistente.
        mvc.perform(delete("/api/clientes/999999").header("Authorization", tokenOperador))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorNaoAlteraTarifa() throws Exception {
        mvc.perform(post("/api/tarifas").header("Authorization", tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"descricao\": \"X\", \"valorHora\": 10, \"valorDiaria\": 50, \"valorMensalidade\": 200 }"))
                .andExpect(status().isForbidden());
    }

    // ---- OPERADOR continua operando o dia a dia ----

    @Test
    void operadorAcessaCadastrosGerais() throws Exception {
        mvc.perform(get("/api/clientes").header("Authorization", tokenOperador))
                .andExpect(status().isOk());
        mvc.perform(get("/api/movimentacoes/patio").header("Authorization", tokenOperador))
                .andExpect(status().isOk());
    }

    // ---- ADMIN passa pela autorizacao ----

    @Test
    void adminCriaUsuario() throws Exception {
        mvc.perform(post("/api/usuarios").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(NOVO_USUARIO))
                .andExpect(status().isCreated());
    }

    @Test
    void adminListaUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    void adminPassaNaAutorizacaoDeExclusao() throws Exception {
        // Admin nao e barrado por role; o 404 prova que chegou ao handler (id inexistente).
        mvc.perform(delete("/api/clientes/999999").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    // ---- Sem token continua 401 (autenticacao antes de autorizacao) ----

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
    }
}
