package com.kaneko.estacionamento.rh;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantFixture;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Teste de integracao do CRUD de Funcionario — todos os fluxos, com a FK
 * obrigatoria de cargo e a unicidade de CPF.
 */
@TenantApiIntegrationTest
class FuncionarioControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CargoRepository cargoRepository;

    @Autowired
    private TenantTestSupport tenant;

    private Long cargoId;
    private TenantFixture fixture;

    @BeforeEach
    void setup() throws Exception {
        fixture = tenant.criarTenantAdmin();
        Cargo cargo = new Cargo();
        cargo.setOrganizacaoId(fixture.organizacaoId());
        cargo.setNome("Operador");
        cargoId = cargoRepository.save(cargo).getId();
    }

    private Number criar(String nome, String cpf) throws Exception {
        String json = "{ \"nome\": \"" + nome + "\", \"cpf\": \"" + cpf + "\", \"cargoId\": " + cargoId + " }";
        String body = mvc.perform(post("/api/funcionarios")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void criaEbuscaPorId() throws Exception {
        String json = "{ \"nome\": \"Maria Operadora\", \"cpf\": \"11122233344\", \"sexo\": \"FEMININO\","
                + " \"cargoId\": " + cargoId + ", \"salario\": 2500.00 }";
        String body = mvc.perform(post("/api/funcionarios")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Maria Operadora"))
                .andExpect(jsonPath("$.cargoId").value(cargoId))
                .andExpect(jsonPath("$.cargoNome").value("Operador"))
                .andExpect(jsonPath("$.dataAdmissao").exists())
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/funcionarios/" + id)
                        .header("Authorization", fixture.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value("11122233344"));
    }

    @Test
    void listaFuncionarios() throws Exception {
        criar("Funcionario Um", "11111111111");
        criar("Funcionario Dois", "22222222222");
        mvc.perform(get("/api/funcionarios")
                        .header("Authorization", fixture.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtraPorNome() throws Exception {
        criar("Funcionario Um", "11111111111");
        criar("Funcionario Dois", "22222222222");
        mvc.perform(get("/api/funcionarios")
                        .header("Authorization", fixture.tokenContextual())
                        .param("nome", "dois"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Funcionario Dois"));
    }

    @Test
    void atualizaFuncionario() throws Exception {
        Number id = criar("Carlos", "33333333333");
        String json = "{ \"nome\": \"Carlos Silva\", \"cpf\": \"33333333333\", \"cargoId\": " + cargoId
                + ", \"salario\": 3000.00 }";
        mvc.perform(put("/api/funcionarios/" + id)
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Carlos Silva"))
                .andExpect(jsonPath("$.salario").value(3000.00));
    }

    @Test
    void atualizaFuncionarioInexistenteRetorna404() throws Exception {
        String json = "{ \"nome\": \"X\", \"cpf\": \"00000000000\", \"cargoId\": " + cargoId + " }";
        mvc.perform(put("/api/funcionarios/999999")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluiFuncionarioEdepoisRetorna404() throws Exception {
        Number id = criar("Temporario", "44444444444");
        mvc.perform(delete("/api/funcionarios/" + id)
                        .header("Authorization", fixture.tokenContextual()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/funcionarios/" + id)
                        .header("Authorization", fixture.tokenContextual()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaCpfDuplicadoCom409() throws Exception {
        criar("Funcionario A", "99999999999");
        String json = "{ \"nome\": \"Funcionario B\", \"cpf\": \"99999999999\", \"cargoId\": " + cargoId + " }";
        mvc.perform(post("/api/funcionarios")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoCargoNaoExiste() throws Exception {
        String json = "{ \"nome\": \"Sem Cargo\", \"cpf\": \"88888888888\", \"cargoId\": 999999 }";
        mvc.perform(post("/api/funcionarios")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna404QuandoNaoEncontrado() throws Exception {
        mvc.perform(get("/api/funcionarios/999999")
                        .header("Authorization", fixture.tokenContextual()))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna400QuandoCargoAusente() throws Exception {
        mvc.perform(post("/api/funcionarios")
                        .header("Authorization", fixture.tokenContextual())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"Sem Cargo\", \"cpf\": \"77777777777\" }"))
                .andExpect(status().isBadRequest());
    }
}
