package com.kaneko.estacionamento.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class DemoAllScreensSeedServiceTest {

    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    private final DemoAllScreensSeedService service = new DemoAllScreensSeedService(jdbc);

    @Test
    void configuraIdentidadesEExecutaSeedTransacionalIdempotente() {
        when(jdbc.queryForObject(anyString(), Mockito.eq(String.class), Mockito.any()))
                .thenReturn("admin", "operador");

        service.seed(" admin ", " operador ");

        InOrder ordem = inOrder(jdbc);
        ordem.verify(jdbc).queryForObject(
                "select set_config('app.demo_admin_login', ?, true)", String.class, "admin");
        ordem.verify(jdbc).queryForObject(
                "select set_config('app.demo_operator_login', ?, true)", String.class, "operador");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ordem.verify(jdbc).execute(sql.capture());
        assertThat(sql.getValue())
                .contains("insert into usuario")
                .contains("insert into movimento_estoque")
                .contains("insert into nota_entrada")
                .contains("insert into nota_saida")
                .contains("insert into nota_servico")
                .contains("insert into ordem_compra")
                .contains("insert into movimentacao")
                .contains("insert into pedido")
                .contains("insert into tentativa_acesso");
    }

    @Test
    void rejeitaLoginVazioAntesDeAcessarBanco() {
        assertThatThrownBy(() -> service.seed(" ", "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(jdbc, Mockito.never()).execute(anyString());
    }
}
