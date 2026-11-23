package com.kaneko.estacionamento.patio;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jayway.jsonpath.JsonPath;
import com.kaneko.estacionamento.common.security.JwtService;
import com.kaneko.estacionamento.support.tenant.TenantApiIntegrationTest;
import com.kaneko.estacionamento.support.tenant.TenantTestSupport;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@TenantApiIntegrationTest
class PatioCentavosMovimentosApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired TenantTestSupport tenants;
    @Autowired JwtService jwt;
    private static final String BASE="/api/v1/patio";

    @Test void eventoNovoEntradaFechaCentavosUmaVezEConservaOsEFiscal()throws Exception{
        var a=tenants.criarTenantAdmin();String token=a.tokenContextual();long org=a.organizacaoId();
        long patio=patio(org),evento=evento(org,patio,"ENTRADA");
        long turno=id(postar(token,BASE+"/turnos","{\"fundoTroco\":0}").andExpect(status().isCreated()));
        String entrada="""
            {"placa":"CEN1A23","patioId":%d,"eventoId":%d,"chaveEntrada":"%s","meio":"DINHEIRO",
             "valorEsperado":"25.130000","recebido":"30.000000"}
            """.formatted(patio,evento,UUID.randomUUID());
        long mov=id(postar(token,BASE+"/entradas",entrada).andExpect(status().isCreated())
            .andExpect(jsonPath("$.evento.valor").value("25.130000"))
            .andExpect(jsonPath("$.evento.valorCalculado").value("25.125000"))
            .andExpect(jsonPath("$.evento.diferencaArredondamento").value("0.005000"))
            .andExpect(jsonPath("$.troco").value("4.870000")));
        postar(token,BASE+"/entradas",entrada).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(mov));
        String pag=pagamento(token,mov,turno,"SEM_COBRANCA");
        postar(token,BASE+"/movimentacoes/"+mov+"/saida",pag).andExpect(status().isOk());
        postar(token,BASE+"/movimentacoes/"+mov+"/saida",pag).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT valor_total_micros FROM ordem_servico WHERE organizacao_id=? AND movimentacao_patio_id=?",
            BigDecimal.class,org,mov)).isEqualByComparingTo("25130000");
        assertThat(jdbc.queryForObject("SELECT valor_micros FROM patio_evento_fiscal WHERE movimentacao_id=?",BigDecimal.class,mov)).isEqualByComparingTo("25130000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conta_receber WHERE organizacao_id=?",Long.class,org)).isZero();
        mvc.perform(get(BASE+"/caixa").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.aberto.totalRecebido").value("25.130000"));
        mvc.perform(get(BASE+"/movimentacoes/"+mov+"/comprovante").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.valorCalculado").value("25.125000")).andExpect(jsonPath("$.valorDevido").value("25.130000"));
        imutavel("UPDATE patio_cobranca_centavos SET valor_devido_micros=9990000 WHERE movimentacao_id=?",mov,"Snapshot");
        imutavel("DELETE FROM patio_cobranca_centavos WHERE movimentacao_id=?",mov,"Snapshot");
    }

    @Test void opcaoCompatibilidadeExibeDevidoERejeitaEstadoAnteriorComPrecoBase()throws Exception{
        var a=tenants.criarTenantAdmin();String token=a.tokenContextual();long org=a.organizacaoId();
        long patio=patio(org),evento=evento(org,patio,"ENTRADA");
        jdbc.update("UPDATE patio_evento_cobranca SET valor_micros=25123456 WHERE organizacao_id=? AND evento_id=?",org,evento);
        postar(token,BASE+"/turnos","{\"fundoTroco\":0}").andExpect(status().isCreated());
        mvc.perform(get(BASE+"/caixa").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.eventos[0].valor").value("25.120000"))
            .andExpect(jsonPath("$.eventos[0].valorDevido").value("25.120000"))
            .andExpect(jsonPath("$.eventos[0].valorCalculado").value("25.123456"));
        String obsoleta="""
            {"placa":"CEN6A23","patioId":%d,"eventoId":%d,"chaveEntrada":"%s","meio":"DINHEIRO",
             "valorEsperado":"25.123456","recebido":"30.000000"}
            """.formatted(patio,evento,UUID.randomUUID());
        postar(token,BASE+"/entradas",obsoleta).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patio_evento_estadia WHERE organizacao_id=?",Long.class,org)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patio_pagamento_dinheiro WHERE organizacao_id=?",Long.class,org)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ordem_servico WHERE organizacao_id=?",Long.class,org)).isZero();
        String atual=obsoleta.replace("\"valorEsperado\":\"25.123456\"","\"valorEsperado\":\"25.120000\"");
        long mov=id(postar(token,BASE+"/entradas",atual).andExpect(status().isCreated())
            .andExpect(jsonPath("$.troco").value("4.880000")));
        postar(token,BASE+"/entradas",atual).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(mov));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patio_evento_estadia WHERE organizacao_id=?",Long.class,org)).isEqualTo(1);
    }

    @Test void eventoNovoNaSaidaCongelaBaseEHistoricoAnteriorPermaneceSeisCasas()throws Exception{
        var a=tenants.criarTenantAdmin();String token=a.tokenContextual();long org=a.organizacaoId();
        long patio=patio(org),evento=evento(org,patio,"SAIDA");
        long turno=id(postar(token,BASE+"/turnos","{\"fundoTroco\":0}").andExpect(status().isCreated()));
        long novo=id(postar(token,BASE+"/entradas","""
            {"placa":"CEN2A23","patioId":%d,"eventoId":%d,"chaveEntrada":"%s"}
            """.formatted(patio,evento,UUID.randomUUID())).andExpect(status().isCreated()));
        String pagamento=pagamento(token,novo,turno,"PIX");
        postar(token,BASE+"/movimentacoes/"+novo+"/saida",pagamento).andExpect(status().isOk())
            .andExpect(jsonPath("$.valor").value("25.130000"));
        em.flush();
        // Registros anteriores não têm marcador V107. Fixture histórica é inserida assim, sem alterar a cobrança nova.
        long veiculo=jdbc.queryForObject("INSERT INTO veiculo(organizacao_id,placa,ativo,version) VALUES(?,'CEN2A24',true,0) RETURNING id",Long.class,org);
        long legado=jdbc.queryForObject("""
            INSERT INTO movimentacao(organizacao_id,veiculo_id,patio_id,patio_turno_entrada_id,tipo,status,data_entrada)
            VALUES(?,?,?,?,'AVULSO','ABERTO',now()-interval '1 hour') RETURNING id
            """,Long.class,org,veiculo,patio,turno);
        jdbc.update("""
            INSERT INTO patio_evento_estadia(movimentacao_id,organizacao_id,evento_id,patio_id,chave_entrada,modo,valor_micros)
            VALUES(?,?,?,?,?,'SAIDA',25123456)
            """,legado,org,evento,patio,UUID.randomUUID());
        jdbc.update("UPDATE alocacao_patio_evento SET ocupacao_atual=ocupacao_atual+1 WHERE organizacao_id=? AND evento_id=?",org,evento);
        em.clear(); // A fixture JDBC deve invalidar a alocação já carregada pela saída anterior.
        String historico=pagamento(token,legado,turno,"PIX");
        postar(token,BASE+"/movimentacoes/"+legado+"/saida",historico).andExpect(status().isOk())
            .andExpect(jsonPath("$.valor").value("25.123456"))
            .andExpect(jsonPath("$.valorCalculado").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patio_cobranca_centavos WHERE movimentacao_id=?",Long.class,legado)).isZero();
    }

    @Test void comumNovoFechaCentavosMasMovimentacaoAnteriorMantemCotacaoOriginal()throws Exception{
        var a=tenants.criarTenantAdmin();String token=a.tokenContextual();long org=a.organizacaoId();long patio=patio(org);
        long tarifa=id(postar(token,"/api/tarifas","{\"descricao\":\"Hora precisa\",\"valorHora\":12.345678,\"valorDiaria\":80,\"valorMensalidade\":150}").andExpect(status().isCreated()));
        long turno=id(postar(token,BASE+"/turnos","{\"fundoTroco\":0}").andExpect(status().isCreated()));
        long mov=id(postar(token,BASE+"/entradas","{\"placa\":\"CEN3A23\"}").andExpect(status().isCreated()));
        jdbc.update("UPDATE movimentacao SET data_entrada=now()-interval '20 minutes' WHERE id=?",mov);em.clear();
        String previa=postar(token,BASE+"/movimentacoes/"+mov+"/previa-saida","{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.valor").value("12.350000"))
            .andExpect(jsonPath("$.valorCalculado").value("12.345678"))
            .andExpect(jsonPath("$.diferencaArredondamento").value("0.004322")).andReturn().getResponse().getContentAsString();
        postar(token,BASE+"/movimentacoes/"+mov+"/saida","{\"turnoId\":"+turno+",\"chave\":\""+JsonPath.read(previa,"$.chave")+"\",\"meio\":\"PIX\"}")
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT valor_devido_micros FROM patio_cobranca_centavos WHERE movimentacao_id=?",BigDecimal.class,mov)).isEqualByComparingTo("12350000");
        long veiculo=jdbc.queryForObject("INSERT INTO veiculo(organizacao_id,placa,ativo,version) VALUES(?,'CEN3A24',true,0) RETURNING id",Long.class,org);
        long legado=jdbc.queryForObject("""
            INSERT INTO movimentacao(organizacao_id,veiculo_id,patio_id,patio_turno_entrada_id,tarifa_id,tipo,status,data_entrada)
            VALUES(?,?,?,?,?,'AVULSO','ABERTO',now()-interval '20 minutes') RETURNING id
            """,Long.class,org,veiculo,patio,turno,tarifa);
        String antiga=pagamento(token,legado,turno,"PIX");
        postar(token,BASE+"/movimentacoes/"+legado+"/saida",antiga).andExpect(status().isOk())
            .andExpect(jsonPath("$.valor").value("12.345678")).andExpect(jsonPath("$.valorCalculado").doesNotExist());
    }

    @Test void crudContextualNaoContornaCentavosNemRecalculaMovimentoAnterior()throws Exception{
        var a=tenants.criarTenantAdmin();String token=a.tokenContextual();long org=a.organizacaoId();long patio=patio(org);
        long tarifa=id(postar(token,"/api/tarifas","{\"descricao\":\"Tarifa CRUD precisa\",\"valorHora\":12.345678,\"valorDiaria\":80,\"valorMensalidade\":150}").andExpect(status().isCreated()));
        long veiculo=jdbc.queryForObject("INSERT INTO veiculo(organizacao_id,placa,ativo,version) VALUES(?,'CEN5A23',true,0) RETURNING id",Long.class,org);
        long nova=id(postar(token,"/api/movimentacoes/entrada","{\"veiculoId\":"+veiculo+",\"patioId\":"+patio+"}").andExpect(status().isCreated()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patio_cobranca_centavos WHERE organizacao_id=? AND movimentacao_id=?",Long.class,org,nova)).isEqualTo(1);
        jdbc.update("UPDATE movimentacao SET data_entrada=now()-interval '20 minutes' WHERE id=?",nova);em.clear();
        postar(token,"/api/movimentacoes/"+nova+"/saida","{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.valorCobrado").value(12.35));
        assertThat(jdbc.queryForObject("SELECT valor_cobrado_micros FROM movimentacao WHERE id=?",BigDecimal.class,nova)).isEqualByComparingTo("12350000");
        assertThat(jdbc.queryForObject("SELECT valor_calculado_micros FROM patio_cobranca_centavos WHERE movimentacao_id=?",BigDecimal.class,nova)).isEqualByComparingTo("12345678");
        assertThat(jdbc.queryForObject("SELECT diferenca_micros FROM patio_cobranca_centavos WHERE movimentacao_id=?",BigDecimal.class,nova)).isEqualByComparingTo("4322");
        // Repetição do contrato CRUD mantém o conflito existente, sem uma segunda gravação financeira.
        postar(token,"/api/movimentacoes/"+nova+"/saida","{}").andExpect(status().isConflict());
        long veiculoAntigo=jdbc.queryForObject("INSERT INTO veiculo(organizacao_id,placa,ativo,version) VALUES(?,'CEN5A24',true,0) RETURNING id",Long.class,org);
        long antiga=jdbc.queryForObject("""
            INSERT INTO movimentacao(organizacao_id,veiculo_id,patio_id,tarifa_id,tipo,status,data_entrada)
            VALUES(?,?,?,?,'AVULSO','ABERTO',now()-interval '20 minutes') RETURNING id
            """,Long.class,org,veiculoAntigo,patio,tarifa);
        postar(token,"/api/movimentacoes/"+antiga+"/saida","{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.valorCobrado").value(12.345678));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patio_cobranca_centavos WHERE movimentacao_id=?",Long.class,antiga)).isZero();
        assertThat(jdbc.queryForObject("SELECT valor_cobrado_micros FROM movimentacao WHERE id=?",BigDecimal.class,antiga)).isEqualByComparingTo("12345678");
    }

    @Test void suprimentoESangriaAuditaveisIdempotentesEComSaldoSemAcessoAoTurnoAlheio()throws Exception{
        var a=tenants.criarTenantAdmin();String token=a.tokenContextual();long org=a.organizacaoId();
        long turno=id(postar(token,BASE+"/turnos","{\"fundoTroco\":10}").andExpect(status().isCreated()));
        String rota=BASE+"/turnos/"+turno+"/movimentos-dinheiro";
        String suprimento="{\"chave\":\""+UUID.randomUUID()+"\",\"tipo\":\"SUPRIMENTO\",\"valor\":20,\"motivo\":\"Reposição de troco do início\"}";
        postar(token,rota,suprimento).andExpect(status().isOk()).andExpect(jsonPath("$.dinheiroEsperado").value("30.000000"))
            .andExpect(jsonPath("$.totalRecebido").value("0.000000"));
        postar(token,rota,suprimento).andExpect(status().isOk());
        postar(token,rota,suprimento.replace("\"valor\":20","\"valor\":21")).andExpect(status().isConflict());
        postar(token,rota,"{\"chave\":\""+UUID.randomUUID()+"\",\"tipo\":\"SANGRIA\",\"valor\":31,\"motivo\":\"Retirada para cofre interno\"}").andExpect(status().isConflict());
        postar(token,rota,"{\"chave\":\""+UUID.randomUUID()+"\",\"tipo\":\"SANGRIA\",\"valor\":25,\"motivo\":\"Retirada para cofre interno\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.dinheiroEsperado").value("5.000000")).andExpect(jsonPath("$.sangrias").value("25.000000"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patio_movimento_dinheiro WHERE organizacao_id=?",Long.class,org)).isEqualTo(2);
        long movimentoDinheiro=jdbc.queryForObject("SELECT min(id) FROM patio_movimento_dinheiro WHERE organizacao_id=?",Long.class,org);
        imutavel("UPDATE patio_movimento_dinheiro SET valor_micros=1 WHERE id=?",movimentoDinheiro,"imutáveis");
        imutavel("DELETE FROM patio_movimento_dinheiro WHERE id=?",movimentoDinheiro,"imutáveis");
        mvc.perform(get(rota).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(BASE+"/turnos").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].resumo.suprimentos").value("20.000000"));
        var caixa=tenants.criarUsuario("caixa_centavos_"+org,PerfilUsuario.OPERADOR);
        long membro=tenants.criarMembership(org,caixa.getId(),token);
        tenants.concederPapel(org,membro,token,"CAIXA_PATIO");
        String caixaToken=tenants.selecionarOrganizacao(org,TenantTestSupport.bearer(jwt.gerarToken(caixa)));
        mvc.perform(get(BASE+"/turnos").header("Authorization",caixaToken)).andExpect(status().isForbidden());
        postar(caixaToken,rota,suprimento).andExpect(status().isNotFound());
        mvc.perform(get(rota).header("Authorization",caixaToken)).andExpect(status().isNotFound());
        var outro=tenants.criarTenantAdmin();
        mvc.perform(get(rota).header("Authorization",outro.tokenContextual())).andExpect(status().isNotFound());
        mvc.perform(get(BASE+"/turnos").header("Authorization",outro.tokenContextual())).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        postar(token,BASE+"/turnos/"+turno+"/fechamento","{\"dinheiroContado\":5}").andExpect(status().isOk())
            .andExpect(jsonPath("$.diferencaDinheiro").value("0.000000"));
        postar(token,rota,suprimento).andExpect(status().isOk());
        postar(token,rota,suprimento.replace(UUID.fromString(JsonPath.read(suprimento,"$.chave")).toString(),UUID.randomUUID().toString()))
            .andExpect(status().isConflict());
    }
    @Test void precoPositivoQueFechaZeroEhRejeitadoSemCriarEstadia()throws Exception{
        var a=tenants.criarTenantAdmin();long p=patio(a.organizacaoId()),e=evento(a.organizacaoId(),p,"ENTRADA");
        mvc.perform(put("/api/v1/events/"+e+"/cobranca-patio").header("Authorization",a.tokenContextual())
            .contentType(MediaType.APPLICATION_JSON).content("{\"modo\":\"ENTRADA\",\"valor\":\"0.004999\"}"))
            .andExpect(status().isConflict());
        // Uma configuração histórica também não deve autorizar novas entradas positivas gratuitas.
        jdbc.update("UPDATE patio_evento_cobranca SET valor_micros=4999 WHERE organizacao_id=? AND evento_id=?",a.organizacaoId(),e);
        postar(a.tokenContextual(),BASE+"/turnos","{\"fundoTroco\":0}").andExpect(status().isCreated());
        postar(a.tokenContextual(),BASE+"/entradas","""
            {"placa":"CEN4A23","patioId":%d,"eventoId":%d,"chaveEntrada":"%s","meio":"PIX","valorEsperado":"0.004999"}
            """.formatted(p,e,UUID.randomUUID())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patio_evento_estadia WHERE organizacao_id=?",Long.class,a.organizacaoId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ordem_servico WHERE organizacao_id=?",Long.class,a.organizacaoId())).isZero();
    }
    private long patio(long org){
        return jdbc.queryForObject("INSERT INTO patio(organizacao_id,nome,capacidade_vagas,capacidade_operacional,ativo,version) VALUES(?,'Pátio Centavos',20,20,true,0) RETURNING id",Long.class,org);
    }
    private long evento(long org,long patio,String modo){
        long pais=jdbc.queryForObject("INSERT INTO pais(nome) VALUES(?) RETURNING id",Long.class,"País Centavos "+org);
        long estado=jdbc.queryForObject("INSERT INTO estado(nome,uf,pais_id) VALUES('Paraná','PR',?) RETURNING id",Long.class,pais);
        long cidade=jdbc.queryForObject("INSERT INTO cidade(nome,estado_id) VALUES('Cidade Centavos',?) RETURNING id",Long.class,estado);
        long local=jdbc.queryForObject("INSERT INTO unidade_operacional(organizacao_id,nome,tipo,endereco,bairro,cep,cidade_id,fuso_horario) VALUES(?,'Arena Centavos','VENUE','Rua A','Centro','85000-000',?,'America/Sao_Paulo') RETURNING id",Long.class,org,cidade);
        long evento=jdbc.queryForObject("INSERT INTO evento(organizacao_id,venue_id,nome,inicio,fim,fuso_horario,status,politica_reentrada) VALUES(?,?,'Evento Centavos',now()-interval '1 hour',now()+interval '5 hours','America/Sao_Paulo','EM_OPERACAO','ENTRADA_UNICA') RETURNING id",Long.class,org,local);
        jdbc.update("INSERT INTO alocacao_patio_evento(organizacao_id,evento_id,patio_id,inicio,fim,capacidade_operacional,capacidade_vendavel,capacidade_reservada) VALUES(?,?,?,now()-interval '1 hour',now()+interval '5 hours',20,0,0)",org,evento,patio);
        jdbc.update("INSERT INTO patio_evento_cobranca(evento_id,organizacao_id,modo,valor_micros) VALUES(?,?,?,25125000)",evento,org,modo);
        return evento;
    }
    private String pagamento(String token,long mov,long turno,String meio)throws Exception{
        var json=postar(token,BASE+"/movimentacoes/"+mov+"/previa-saida","{}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "{\"turnoId\":"+turno+",\"chave\":\""+JsonPath.read(json,"$.chave")+"\",\"meio\":\""+meio+"\"}";
    }
    private ResultActions postar(String token,String rota,String body)throws Exception{
        return mvc.perform(post(rota).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private void imutavel(String sql,long id,String mensagem){
        jdbc.execute((ConnectionCallback<Void>)c->{
            var ponto=c.setSavepoint();
            try(var comando=c.prepareStatement(sql)){
                comando.setLong(1,id);
                try{comando.executeUpdate();fail("A estrutura financeira deveria ser imutável.");}
                catch(java.sql.SQLException esperado){c.rollback(ponto);assertThat(esperado.getMessage()).contains(mensagem);}
            }finally{c.releaseSavepoint(ponto);}
            return null;
        });
    }
    private static long id(ResultActions r)throws Exception{
        return ((Number)JsonPath.read(r.andReturn().getResponse().getContentAsString(),"$.id")).longValue();
    }
}
