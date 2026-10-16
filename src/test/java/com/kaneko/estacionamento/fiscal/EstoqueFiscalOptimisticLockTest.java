package com.kaneko.estacionamento.fiscal;

import static org.assertj.core.api.Assertions.assertThat;

import com.kaneko.estacionamento.common.web.ApiExceptionHandler;
import com.kaneko.estacionamento.conveniencia.Marca;
import com.kaneko.estacionamento.conveniencia.MarcaRepository;
import com.kaneko.estacionamento.conveniencia.Produto;
import com.kaneko.estacionamento.conveniencia.ProdutoRepository;
import com.kaneko.estacionamento.conveniencia.UnidadeMedida;
import com.kaneko.estacionamento.conveniencia.UnidadeMedidaRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class EstoqueFiscalOptimisticLockTest {

    @Autowired private ProdutoRepository produtos;
    @Autowired private MarcaRepository marcas;
    @Autowired private UnidadeMedidaRepository unidades;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void entradaESaidaConcorrentesNoProdutoHistoricoNaoPerdemAtualizacao() throws Exception {
        Long produtoId = criarProdutoHistorico();
        CountDownLatch carregados = new CountDownLatch(2);
        CountDownLatch executar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> entrada = executor.submit(
                    () -> alterarEstoque(produtoId, new BigDecimal("10"), carregados, executar));
            Future<Throwable> saida = executor.submit(
                    () -> alterarEstoque(produtoId, new BigDecimal("-5"), carregados, executar));
            assertThat(carregados.await(10, TimeUnit.SECONDS)).isTrue();
            executar.countDown();

            List<Throwable> falhas = Stream.of(entrada.get(10, TimeUnit.SECONDS),
                            saida.get(10, TimeUnit.SECONDS))
                    .filter(java.util.Objects::nonNull).toList();
            assertThat(falhas).hasSize(1);
            assertThat(falhas.getFirst()).isInstanceOf(OptimisticLockingFailureException.class);

            Produto persistido = produtos.findById(produtoId).orElseThrow();
            assertThat(persistido.getVersion()).isEqualTo(1L);
            assertThat(persistido.getQuantidade()).isIn(
                    new BigDecimal("110.000"), new BigDecimal("95.000"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void conflitoOtimistaRetorna409ComRetryExplicito() {
        var resposta = new ApiExceptionHandler().handleConcorrenciaOtimista(
                new OptimisticLockingFailureException("concorrencia real"));
        assertThat(resposta.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(resposta.getDetail()).contains("Recarregue").contains("tente novamente")
                .contains("revertida");
    }

    private Throwable alterarEstoque(Long produtoId, BigDecimal delta,
            CountDownLatch carregados, CountDownLatch executar) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Produto produto = produtos.findByIdAndOrganizacaoIdIsNull(produtoId).orElseThrow();
                carregados.countDown();
                await(executar);
                produto.setQuantidade(produto.getQuantidade().add(delta));
                produtos.saveAndFlush(produto);
            });
            return null;
        } catch (Throwable ex) {
            return ex;
        }
    }

    private Long criarProdutoHistorico() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String sufixo = UUID.randomUUID().toString().substring(0, 8);
            Marca marca = new Marca();
            marca.setNome("Marca fiscal " + sufixo);
            marca = marcas.save(marca);
            UnidadeMedida unidade = new UnidadeMedida();
            unidade.setNome("Unidade fiscal " + sufixo);
            unidade.setSigla("F" + sufixo.substring(0, 5));
            unidade = unidades.save(unidade);
            Produto produto = new Produto();
            produto.setNome("Produto fiscal " + sufixo);
            produto.setMarca(marca);
            produto.setUnidadeMedida(unidade);
            produto.setQuantidade(new BigDecimal("100.000"));
            return produtos.saveAndFlush(produto).getId();
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timeout aguardando concorrencia do teste");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("teste interrompido", ex);
        }
    }
}
