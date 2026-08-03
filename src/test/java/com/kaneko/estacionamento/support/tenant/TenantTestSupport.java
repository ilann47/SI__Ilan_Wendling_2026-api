package com.kaneko.estacionamento.support.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioService;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

public final class TenantTestSupport {

    private static final AtomicLong SEQUENCIA = new AtomicLong(1);
    private static final String SENHA = "senha123";

    private final MockMvc mvc;
    private final UsuarioService usuarioService;
    private final OrganizacaoService organizacaoService;

    public TenantTestSupport(
            MockMvc mvc,
            UsuarioService usuarioService,
            OrganizacaoService organizacaoService) {
        this.mvc = mvc;
        this.usuarioService = usuarioService;
        this.organizacaoService = organizacaoService;
    }

    public TenantFixture criarTenantAdmin() throws Exception {
        long sequencia = SEQUENCIA.getAndIncrement();
        Usuario usuario = criarUsuario("tenant_admin_" + sequencia, PerfilUsuario.ADMIN);
        Long organizacaoId = organizacaoService.criar(new CriarOrganizacaoRequest(
                cnpjValido(sequencia),
                "Organizacao de teste " + sequencia,
                "Tenant " + sequencia,
                "BRL",
                "America/Sao_Paulo",
                "BR-PR",
                "ENTERPRISE")).id();
        String tokenGlobal = autenticar(usuario.getLogin(), SENHA);
        Long membershipId = criarMembership(organizacaoId, usuario.getId(), tokenGlobal);
        String tokenContextual = selecionarOrganizacao(organizacaoId, tokenGlobal);
        return new TenantFixture(
                organizacaoId, usuario.getId(), membershipId, tokenGlobal, tokenContextual);
    }

    public Usuario criarUsuario(String login, PerfilUsuario perfil) {
        return usuarioService.criar(new UsuarioRequest(
                "Usuario de teste", login, SENHA, null, null, perfil));
    }

    public String autenticar(String login, String senha) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return bearer(JsonPath.read(body, "$.token"));
    }

    public Long criarMembership(Long organizacaoId, Long usuarioId, String token) throws Exception {
        String body = mvc.perform(post(
                        "/api/v1/organizations/{organizationId}/memberships", organizacaoId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + usuarioId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    public String selecionarOrganizacao(Long organizacaoId, String token) throws Exception {
        String body = mvc.perform(post("/api/v1/me/active-organization")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":" + organizacaoId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return bearer(JsonPath.read(body, "$.token"));
    }

    public Long concederPapel(
            Long organizacaoId,
            Long membershipId,
            String tokenContextual,
            String codigoPapel) throws Exception {
        String body = mvc.perform(post(
                        "/api/v1/organizations/{organizationId}/memberships/{membershipId}/role-assignments",
                        organizacaoId, membershipId)
                        .header("Authorization", tokenContextual)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"" + codigoPapel + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    public String tokenSemBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization Bearer obrigatorio.");
        }
        return authorization.substring("Bearer ".length());
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String cnpjValido(long sequencia) {
        String base = "%012d".formatted(sequencia % 1_000_000_000_000L);
        int primeiro = digitoCnpj(base, new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        int segundo = digitoCnpj(base + primeiro, new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return base + primeiro + segundo;
    }

    private static int digitoCnpj(String numeros, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += Character.digit(numeros.charAt(i), 10) * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
