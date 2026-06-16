package com.kaneko.estacionamento.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teste de observabilidade: o trace id de correlacao e devolvido no cabecalho da
 * resposta e o trace id recebido do cliente e propagado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ObservabilidadeTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void respostaTrazTraceIdNoCabecalho() throws Exception {
        mvc.perform(get("/api/tarifas"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(header().string("X-Trace-Id", Matchers.not(Matchers.blankOrNullString())));
    }

    @Test
    void traceIdRecebidoEhPropagado() throws Exception {
        mvc.perform(get("/api/tarifas").header("X-Trace-Id", "trace-cliente-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-cliente-123"));
    }
}
