package com.kaneko.estacionamento.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.UsuarioRepository;
import com.kaneko.estacionamento.usuario.UsuarioService;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de integracao da autenticacao — login valido emite token; credenciais
 * invalidas resultam em 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setup() {
        usuarioService.criar(new UsuarioRequest("Operador", "operador", "senha123",
                null, null, PerfilUsuario.OPERADOR));
    }

    @Test
    void loginValidoEmiteToken() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"operador\", \"senha\": \"senha123\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.perfil").value("OPERADOR"));
    }

    @Test
    void loginComSenhaErradaRetorna401() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"operador\", \"senha\": \"errada\" }"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginDeUsuarioInexistenteRetorna401() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"naoexiste\", \"senha\": \"qualquer\" }"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cadastroPublicoCriaContaNeutraNormalizadaEEmiteTokenGlobal() throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "  Maria da Silva  ",
                                  "login": "  Maria.Silva  ",
                                  "email": "  MARIA.SILVA@EXAMPLE.COM  ",
                                  "senha": "senha-segura-123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Maria da Silva"))
                .andExpect(jsonPath("$.login").value("maria.silva"))
                .andExpect(jsonPath("$.email").value("maria.silva@example.com"))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.perfil").value("USUARIO"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        var usuario = usuarioRepository.findByLoginIgnoreCase("maria.silva").orElseThrow();
        Assertions.assertThat(usuario.getNome()).isEqualTo("Maria da Silva");
        Assertions.assertThat(usuario.getEmail()).isEqualTo("maria.silva@example.com");
        Assertions.assertThat(usuario.getPerfil()).isEqualTo(PerfilUsuario.USUARIO);
        Assertions.assertThat(usuario.getAtivo()).isTrue();
        Assertions.assertThat(usuario.getFuncionario()).isNull();
        Assertions.assertThat(passwordEncoder.matches("senha-segura-123", usuario.getSenha())).isTrue();

        String token = JsonPath.read(body, "$.token");
        JwtService.Claims claims = jwtService.validar(token);
        Assertions.assertThat(claims.usuarioId()).isEqualTo(usuario.getId());
        Assertions.assertThat(claims.perfil()).isEqualTo("USUARIO");
        Assertions.assertThat(claims.possuiAlgumClaimDeContexto()).isFalse();
    }

    @Test
    void cadastroPublicoRecusaLoginOuEmailDuplicadoSemDiferenciarMaiusculas() throws Exception {
        cadastrar("Primeira Pessoa", "nova.pessoa", "nova.pessoa@example.com", "senha-segura-123")
                .andExpect(status().isCreated());

        cadastrar("Login Repetido", "NOVA.PESSOA", "outro@example.com", "senha-segura-123")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito"))
                .andExpect(jsonPath("$.detail").value("Login ou e-mail já cadastrado."));
        cadastrar("Email Repetido", "outro.login", "NOVA.PESSOA@EXAMPLE.COM", "senha-segura-123")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito"))
                .andExpect(jsonPath("$.detail").value("Login ou e-mail já cadastrado."));
    }

    @Test
    void cadastroPublicoValidaEmailESenha() throws Exception {
        cadastrar("Email Invalido", "email.invalido", "nao-e-email", "senha-segura-123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisicao invalida"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erros.email").value("E-mail inválido."));
        cadastrar("Senha Curta", "senha.curta", "senha.curta@example.com", "1234567")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.senha")
                        .value("A senha deve ter entre 8 e 72 caracteres."));
    }

    @Test
    void cadastroPublicoRecusaCamposAdministrativos() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Tentativa Admin",
                                  "login": "tentativa.admin",
                                  "email": "tentativa.admin@example.com",
                                  "senha": "senha-segura-123",
                                  "perfil": "ADMIN"
                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisicao invalida"))
                .andExpect(jsonPath("$.detail").value("Ha campos invalidos na requisicao."));
    }

    @Test
    void openApiExpoeSomenteCamposPublicosNoCadastro() throws Exception {
        String spec = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = new ObjectMapper().readTree(spec);
        Assertions.assertThat(root.at("/paths/~1api~1auth~1register/post").isMissingNode()).isFalse();
        JsonNode properties = root.at("/components/schemas/CadastroContaRequest/properties");
        Assertions.assertThat(properties.has("nome")).isTrue();
        Assertions.assertThat(properties.has("login")).isTrue();
        Assertions.assertThat(properties.has("email")).isTrue();
        Assertions.assertThat(properties.has("senha")).isTrue();
        Assertions.assertThat(properties.has("perfil")).isFalse();
        Assertions.assertThat(properties.has("ativo")).isFalse();
        Assertions.assertThat(properties.has("funcionarioId")).isFalse();
    }

    private org.springframework.test.web.servlet.ResultActions cadastrar(
            String nome, String login, String email, String senha) throws Exception {
        return mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                        "nome", nome,
                        "login", login,
                        "email", email,
                        "senha", senha))));
    }
}
