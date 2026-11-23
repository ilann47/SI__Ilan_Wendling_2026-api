package com.kaneko.estacionamento.patio;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.evento.EventoRepository;
import com.kaneko.estacionamento.evento.StatusEvento;
import com.kaneko.estacionamento.inventario.alocacao.AlocacaoPatioEventoRepository;
import com.kaneko.estacionamento.ordensservico.OrdemServicoService;
import com.kaneko.estacionamento.ordensservico.StatusOrdemServico;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatioEventoService {
    public enum Modo { ENTRADA, SAIDA }

    public record Cobranca(Modo modo, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valor) {}
    public record Opcao(long eventoId, String nome, long patioId, String patioNome, Modo modo,
                        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valor,
                        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valorCalculado,
                        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valorDevido,
                        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal diferencaArredondamento) {
        Opcao(long eventoId,String nome,long patioId,String patioNome,Modo modo,BigDecimal valor){
            this(eventoId,nome,patioId,patioNome,modo,PatioCobrancaCentavos.calcular(valor));
        }
        private Opcao(long eventoId,String nome,long patioId,String patioNome,Modo modo,PatioCobrancaCentavos.Valor centavos){
            this(eventoId,nome,patioId,patioNome,modo,centavos.valorDevido(),centavos.valorCalculado(),
                centavos.valorDevido(),centavos.diferencaArredondamento());
        }
    }
    public record Detalhe(long movimentacaoId, long eventoId, String nome, Modo modo,
                          @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valor,
                          @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valorPago,
                          String fiscalStatus, Long ordemServicoId, String ordemServicoNumero,
                          StatusOrdemServico ordemServicoStatus,
                          @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal valorCalculado,
                          @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal valorDevido,
                          @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal diferencaArredondamento) {}

    private final EventoRepository eventos;
    private final AlocacaoPatioEventoRepository alocacoes;
    private final PatioEventoRepository estadias;
    private final OrdemServicoService ordens;

    PatioEventoService(EventoRepository eventos, AlocacaoPatioEventoRepository alocacoes,
                       PatioEventoRepository estadias, OrdemServicoService ordens) {
        this.eventos = eventos;
        this.alocacoes = alocacoes;
        this.estadias = estadias;
        this.ordens = ordens;
    }

    @Transactional(readOnly = true)
    public Cobranca consultar(long org, long evento) {
        eventos.findStatusByIdAndOrganizacaoId(evento, org).orElseThrow(() -> eventoNaoEncontrado(evento));
        return estadias.cobranca(org, evento).orElse(null);
    }

    @Transactional
    public Cobranca configurar(long org, long evento, Cobranca cobranca) {
        PatioCobrancaCentavos.paraCobrar(cobranca.valor());
        var e = eventos.findByIdAndOrganizacaoIdForUpdate(evento, org)
                .orElseThrow(() -> eventoNaoEncontrado(evento));
        if (List.of(StatusEvento.CANCELADO, StatusEvento.FINALIZADO, StatusEvento.OPERACAO_ENCERRADA,
                StatusEvento.EM_CONCILIACAO).contains(e.getStatus())) {
            throw new ConflitoException("O evento não aceita alterações na cobrança do pátio.");
        }
        estadias.configurar(org, evento, cobranca);
        return estadias.cobranca(org, evento).orElseThrow();
    }

    Cobranca reservarEntrada(long org, long evento, long patio, OffsetDateTime agora) {
        return reservarEntrada(org,evento,patio,agora,cobranca -> {});
    }

    Cobranca reservarEntrada(long org, long evento, long patio, OffsetDateTime agora,
            java.util.function.Consumer<Cobranca> validarPagamento) {
        var e = eventos.findByIdAndOrganizacaoIdForUpdate(evento, org)
                .orElseThrow(() -> eventoNaoEncontrado(evento));
        if (e.getStatus() != StatusEvento.EM_OPERACAO) {
            throw new ConflitoException("Selecione um evento que esteja em operação.");
        }
        var cobranca = estadias.cobranca(org, evento)
                .orElseThrow(() -> new ConflitoException("Configure a cobrança avulsa desse evento."));
        var alocacao = alocacoes.findByEventoAndPatioForUpdate(org, evento, patio)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pátio do evento", patio));
        if (agora.isBefore(alocacao.getInicio()) || !agora.isBefore(alocacao.getFim())) {
            throw new ConflitoException("O pátio está fora da janela de entrada deste evento.");
        }
        long vagasAvulsas = alocacao.getCapacidadeOperacional()
                - alocacao.getCapacidadeVendavel() - alocacao.getCapacidadeReservada();
        if (alocacao.getOcupacaoAtual() >= alocacao.getCapacidadeOperacional()
                || estadias.avulsosAbertos(org, evento, patio) >= vagasAvulsas) {
            throw new ConflitoException("Não há vaga avulsa disponível para este evento e pátio.");
        }
        validarPagamento.accept(cobranca);
        alocacao.registrarEntrada();
        return cobranca;
    }

    void abrirOrdem(long org, long evento, Movimentacao movimentacao, BigDecimal valor) {
        var eventoSelecionado = eventos.findByIdAndOrganizacaoId(evento, org)
                .orElseThrow(() -> eventoNaoEncontrado(evento));
        ordens.abrirParaEstadia(org, movimentacao.getId(), movimentacao.getDataEntrada(), valor, eventoSelecionado.getServico());
    }

    void liberarEntradaCancelada(long org, PatioEventoRepository.Estadia estadia) {
        var alocacao = alocacoes.findByEventoAndPatioForUpdate(org, estadia.eventoId(), estadia.patioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pátio do evento", estadia.patioId()));
        alocacao.registrarSaida();
    }

    void encerrar(long org, PatioEventoRepository.Estadia estadia, OffsetDateTime agora) {
        var alocacao = alocacoes.findByEventoAndPatioForUpdate(org, estadia.eventoId(), estadia.patioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pátio do evento", estadia.patioId()));
        alocacao.registrarSaida();
        Long ordemId = estadia.ordemServicoId();
        if (ordemId == null) {
            var eventoSelecionado = eventos.findByIdAndOrganizacaoId(estadia.eventoId(), org)
                    .orElseThrow(() -> eventoNaoEncontrado(estadia.eventoId()));
            ordemId = ordens.abrirParaEstadia(org, estadia.id(), estadia.dataEntrada(),
                    estadia.valor(), eventoSelecionado.getServico());
        }
        ordens.concluirParaEstadia(org, estadia.id(), ordemId, agora);
        estadias.criarPendenciaFiscal(org, estadia, ordemId, agora);
    }

    static Detalhe detalhe(PatioEventoRepository.Estadia estadia) {
        return new Detalhe(estadia.id(), estadia.eventoId(), estadia.nome(), estadia.modo(), estadia.valor(),
                estadia.movimentoStatus()!=StatusMovimentacao.CANCELADO && !"CANCELADA".equals(estadia.fiscalStatus()) && (estadia.meioEntrada() != null || estadia.saiu())
                    && estadia.ordemServicoStatus()!=StatusOrdemServico.CANCELADA ? estadia.valor() : BigDecimal.ZERO.setScale(6),
                estadia.fiscalStatus(), estadia.ordemServicoId(), estadia.ordemServicoNumero(), estadia.ordemServicoStatus(),
                estadia.centavos()==null?null:estadia.centavos().valorCalculado(),
                estadia.centavos()==null?null:estadia.centavos().valorDevido(),
                estadia.centavos()==null?null:estadia.centavos().diferencaArredondamento());
    }

    private static RecursoNaoEncontradoException eventoNaoEncontrado(long id) {
        return new RecursoNaoEncontradoException("Evento", id);
    }
}
