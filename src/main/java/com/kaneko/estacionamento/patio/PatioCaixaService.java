package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.CampoInvalidoException;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.patio.PatioCaixaRepository.Checkout;
import com.kaneko.estacionamento.patio.PatioCaixaRepository.Turno;
import com.kaneko.estacionamento.patio.dto.VeiculoRequest;
import com.kaneko.estacionamento.pessoas.ClienteTomadorService;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatioCaixaService {
    private static final int VALIDADE_PREVIA_MINUTOS = 3;

    public enum Meio { DINHEIRO, PIX, CARTAO, SEM_COBRANCA }
    public record Caixa(Resumo aberto, Resumo ultimoFechado, List<PatioOpcao> patios,
                        List<PatioEventoService.Opcao> eventos, List<PatioEventoService.Detalhe> estadiasEvento) {}
    public record PatioOpcao(long id, String nome) {}
    public record Resumo(long id, OffsetDateTime abertoEm, OffsetDateTime fechadoEm,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal fundoTroco, long saidas,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal dinheiro,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal pix,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cartao,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalRecebido,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal dinheiroEsperado,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal dinheiroContado,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal diferencaDinheiro,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal suprimentos,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal sangrias) {}
    public record Entrada(long id, String placa, TipoMovimentacao tipo, PatioEventoService.Detalhe evento,
                          @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal recebido,
                          @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal troco) {
        Entrada(long id,String placa,TipoMovimentacao tipo,PatioEventoService.Detalhe evento){this(id,placa,tipo,evento,null,null);}
    }
    public record Previa(long turnoId, UUID chave, String placa, TipoMovimentacao tipo,
                         long minutos, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valor,
                         OffsetDateTime expiraEm, PatioEventoService.Detalhe evento,
                         @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal valorCalculado,
                         @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal valorDevido,
                         @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal diferencaArredondamento) {}
    public record Comprovante(long movimentacaoId, long turnoId, String placa,
                              @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valor,
                              Meio meio, OffsetDateTime registradoEm, PatioEventoService.Detalhe evento,
                              @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal recebido,
                              @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal troco, boolean cancelada,
                              @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal valorCalculado,
                              @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal valorDevido,
                              @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal diferencaArredondamento) {
        Comprovante(long movimento,long turno,String placa,BigDecimal valor,Meio meio,OffsetDateTime instante,
            PatioEventoService.Detalhe evento,BigDecimal recebido,BigDecimal troco,boolean cancelada){
            this(movimento,turno,placa,valor,meio,instante,evento,recebido,troco,cancelada,null,null,null);
        }
    }

    private final PatioCaixaRepository caixa;
    private final MovimentacaoService movimentos;
    private final VeiculoRepository veiculos;
    private final PatioRepository patios;
    private final VeiculoService cadastro;
    private final AuditoriaService auditoria;
    private final PatioEventoService eventos;
    private final PatioEventoRepository estadias;
    private final PatioOperacaoRepository operacao;
    private final PatioCobrancaCentavosRepository centavos;
    private final PatioMovimentoDinheiroRepository movimentosDinheiro;
    private final ClienteTomadorService clientesTomadores;

    PatioCaixaService(PatioCaixaRepository caixa, MovimentacaoService movimentos,
                      VeiculoRepository veiculos, PatioRepository patios, VeiculoService cadastro,
                      AuditoriaService auditoria, PatioEventoService eventos, PatioEventoRepository estadias,
                      PatioOperacaoRepository operacao,PatioCobrancaCentavosRepository centavos,
                      PatioMovimentoDinheiroRepository movimentosDinheiro, ClienteTomadorService clientesTomadores) {
        this.caixa = caixa;
        this.movimentos = movimentos;
        this.veiculos = veiculos;
        this.patios = patios;
        this.cadastro = cadastro;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.estadias = estadias;
        this.operacao = operacao;
        this.centavos=centavos;
        this.movimentosDinheiro=movimentosDinheiro;
        this.clientesTomadores = clientesTomadores;
    }

    @Transactional(readOnly = true)
    public Caixa caixa(UsuarioAutenticado ator) {
        long org = ator.organizacaoId();
        return new Caixa(caixa.aberto(org, ator.userId(), false).map(t -> resumo(org, t)).orElse(null),
                caixa.ultimoFechado(org, ator.userId()).map(t -> resumo(org, t)).orElse(null),
                patios.findByOrganizacaoIdAndAtivoTrueOrderByNomeAscIdAsc(org).stream()
                        .map(p -> new PatioOpcao(p.getId(), p.getNome())).toList(),
                estadias.opcoes(org, OffsetDateTime.now()),
                estadias.abertas(org).stream().map(PatioEventoService::detalhe).toList());
    }

    @Transactional
    public Resumo abrir(UsuarioAutenticado ator, BigDecimal fundo) {
        long org = ator.organizacaoId();
        Turno turno = caixa.abrir(org, ator.userId(), OffsetDateTime.now(), fundo)
                .orElseThrow(() -> new ConflitoException("Você já tem um turno aberto."));
        auditar(org, "PATIO_TURNO_ABERTO", "PATIO_TURNO", turno.id(), Map.of("fundoTroco", fundo));
        return resumo(org, turno);
    }

    @Transactional
    public Resumo fechar(UsuarioAutenticado ator, long id, BigDecimal contado) {
        long org = ator.organizacaoId();
        Turno turno = turnoDoOperador(ator, id);
        if (turno.fechadoEm() != null) {
            if (turno.dinheiroContado().compareTo(contado) == 0) return resumo(org, turno);
            throw new ConflitoException("Este turno já foi fechado.");
        }
        Resumo resumo = resumo(org, caixa.fechar(turno, OffsetDateTime.now(), contado));
        auditar(org, "PATIO_TURNO_FECHADO", "PATIO_TURNO", id, Map.of(
                "dinheiroEsperado", resumo.dinheiroEsperado(), "dinheiroContado", contado,
                "diferencaDinheiro", resumo.diferencaDinheiro(), "totalRecebido", resumo.totalRecebido()));
        return resumo;
    }

    @Transactional
    public Entrada entrada(UsuarioAutenticado ator, String informada, Long patioId, Long eventoId,
                           UUID chave, Meio meio, BigDecimal valorEsperado) {
        return entrada(ator,informada,patioId,eventoId,chave,meio,valorEsperado,null);
    }

    @Transactional
    public Entrada entrada(UsuarioAutenticado ator, String informada, Long patioId, Long eventoId,
                           UUID chave, Meio meio, BigDecimal valorEsperado,BigDecimal recebido) {
        return entrada(ator, informada, patioId, eventoId, chave, meio, valorEsperado, recebido, null);
    }

    @Transactional
    public Entrada entrada(UsuarioAutenticado ator, String informada, Long patioId, Long eventoId,
                           UUID chave, Meio meio, BigDecimal valorEsperado, BigDecimal recebido, PatioTomador informado) {
        long org = ator.organizacaoId();
        String normalizada = PatioPlaca.obrigatoria(informada);
        PatioTomador tomador = PatioTomador.normalizar(informado);
        if (tomador != null && (eventoId == null || meio == null || meio == Meio.SEM_COBRANCA)) {
            throw tomadorForaDoPagamento();
        }
        if (eventoId != null && chave != null) {
            estadias.bloquearChave(org, chave);
            var existente = estadias.porChave(org, chave);
            if (existente.isPresent()) {
                var estadia = existente.get();
                if (estadia.eventoId() != eventoId || !Objects.equals(estadia.patioId(), patioId)
                        || !PatioPlaca.normalizar(estadia.placa()).equals(normalizada) || estadia.meioEntrada() != meio
                        || (estadia.meioEntrada() != null && !Objects.equals(estadia.tomadorPagamento(), tomador))
                        || (meio != null && (valorEsperado == null || estadia.valor().compareTo(valorEsperado) != 0))) {
                    throw new ConflitoException("Esta confirmação já foi usada para outra entrada.");
                }
                var dinheiro=PatioDinheiro.conferir(meio,estadia.valor(),recebido);
                var original=operacao.pagamento(org,estadia.id());
                if(dinheiro.recebido()!=null && original.isPresent() && original.get().recebido()!=null
                    && dinheiro.recebido().compareTo(original.get().recebido())!=0)
                    throw new ConflitoException("Esta entrada já recebeu outro valor em dinheiro.");
                return new Entrada(estadia.id(), estadia.placa(), TipoMovimentacao.AVULSO, PatioEventoService.detalhe(estadia),
                    dinheiro.recebido(),dinheiro.troco());
            }
        }
        if (eventoId == null && (chave != null || meio != null || valorEsperado != null || recebido!=null)) {
            throw new ConflitoException("Selecione o evento para registrar esta cobrança.");
        }
        Turno turno = turnoAberto(ator);
        if (patioId == null) {
            if (eventoId != null) throw new ConflitoException("Selecione o pátio do evento.");
            var disponiveis = patios.findByOrganizacaoIdAndAtivoTrueOrderByNomeAscIdAsc(org);
            if (disponiveis.isEmpty()) throw new ConflitoException("Cadastre um pátio ativo em Instalações para registrar entradas.");
            if (disponiveis.size() > 1) throw new ConflitoException("Selecione em qual pátio o veículo está entrando.");
            patioId = disponiveis.getFirst().getId();
        }
        Patio patio = movimentos.prepararPatioDaEntrada(org, patioId);
        PatioEventoService.Cobranca cobranca = eventoId == null ? null
                : eventos.reservarEntrada(org, eventoId, patioId, OffsetDateTime.now(), configurada -> {
                    validarCobrancaNaEntrada(configurada,chave,meio,valorEsperado);
                    if(configurada.modo()==PatioEventoService.Modo.SAIDA && recebido!=null)
                        throw new ConflitoException("Receba este evento somente na saída.");
                    PatioDinheiro.conferir(meio,PatioCobrancaCentavos.paraCobrar(configurada.valor()).valorDevido(),recebido);
                });
        if (cobranca != null) validarCobrancaNaEntrada(cobranca, chave, meio, valorEsperado);
        if(cobranca!=null && cobranca.modo()==PatioEventoService.Modo.SAIDA && recebido!=null)
            throw new ConflitoException("Receba este evento somente na saída.");
        var snapshot=cobranca==null?null:PatioCobrancaCentavos.paraCobrar(cobranca.valor());
        var dinheiro=PatioDinheiro.conferir(meio,snapshot==null?BigDecimal.ZERO:snapshot.valorDevido(),recebido);
        // O cadastro genérico pode conter a grafia antiga com hífen.
        var veiculo = veiculos.findByOrganizacaoIdAndPlaca(org, normalizada)
                .or(() -> PatioPlaca.grafiaLegada(normalizada).flatMap(legada -> veiculos.findByOrganizacaoIdAndPlaca(org, legada)))
                .orElseGet(() -> cadastro.criar(org, new VeiculoRequest(
                        normalizada, null, null, null, null, null, true)));
        if (cobranca == null) {
            var mov = movimentos.registrarEntradaDeTurno(org, veiculo.getId(), turno.id(), patio);
            return new Entrada(mov.getId(), veiculo.getPlaca(), mov.getTipo(), null);
        }
        var mov = movimentos.registrarEntradaEvento(org, veiculo.getId(), turno.id(), patio);
        centavos.evento(org,mov.getId(),cobranca.modo(),snapshot);
        var efetiva=new PatioEventoService.Cobranca(cobranca.modo(),snapshot.valorDevido());
        estadias.registrar(org, mov.getId(), eventoId, patioId, chave, efetiva, turno.id(), meio, tomador);
        operacao.dinheiro(org,mov.getId(),"ENTRADA",turno.id(),dinheiro);
        eventos.abrirOrdem(org, eventoId, mov, snapshot.valorDevido());
        if (tomador != null) clientesTomadores.cadastrarSeNovo(org, tomador.documento(), tomador.nome());
        auditar(org, "PATIO_ENTRADA_EVENTO", "MOVIMENTACAO", mov.getId(), Map.of(
                "eventoId", eventoId, "turnoId", turno.id(), "valor", snapshot.valorDevido(),
                "valorCalculado",snapshot.valorCalculado(),"diferencaArredondamento",snapshot.diferencaArredondamento(),
                "modo", cobranca.modo().name(), "pagoNaEntrada", meio != null));
        return new Entrada(mov.getId(), veiculo.getPlaca(), mov.getTipo(),
                PatioEventoService.detalhe(estadias.buscar(org, mov.getId()).orElseThrow()),dinheiro.recebido(),dinheiro.troco());
    }

    private static void validarCobrancaNaEntrada(PatioEventoService.Cobranca cobranca, UUID chave, Meio meio,
                                                 BigDecimal valorEsperado) {
        if (chave == null) throw new ConflitoException("Atualize a entrada e tente novamente.");
        if (cobranca.modo() == PatioEventoService.Modo.ENTRADA) {
            if (meio == null || meio == Meio.SEM_COBRANCA) {
                throw new ConflitoException("Confirme o pagamento antes de registrar a entrada.");
            }
            BigDecimal devido = PatioCobrancaCentavos.paraCobrar(cobranca.valor()).valorDevido();
            if (valorEsperado == null || devido.compareTo(valorEsperado) != 0) {
                throw new ConflitoException("O valor do evento mudou. Atualize os dados e confira a cobrança antes de registrar.");
            }
        } else if (meio != null || valorEsperado != null) {
            throw new ConflitoException("A cobrança deste evento deve ser registrada na saída.");
        }
    }

    @Transactional(readOnly = true)
    public PatioEventoService.Detalhe fiscalEvento(UsuarioAutenticado ator, long id) {
        return estadias.buscar(ator.organizacaoId(), id).map(PatioEventoService::detalhe)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estadia de evento", id));
    }

    @Transactional
    public Previa previa(UsuarioAutenticado ator, long id) {
        long org = ator.organizacaoId();
        Turno turno = turnoAberto(ator);
        var mov = movimentos.buscarParaAlterar(org, id);
        exigirMovimentoAberto(mov);
        var estadia = estadias.buscar(org, id);
        var agora = OffsetDateTime.now();
        var atual = caixa.checkout(org, id).filter(p -> p.expiraEm().isAfter(agora));
        if (atual.isPresent() && atual.get().turnoId() != turno.id()) {
            throw new ConflitoException("Outra caixa está conferindo esta saída. Aguarde a validade da prévia.");
        }
        var previa = atual.orElseGet(() -> {
            BigDecimal calculado = estadia.map(e -> e.meioEntrada() == null ? e.valor() : BigDecimal.ZERO.setScale(6))
                    .orElseGet(() -> MovimentacaoService.calcularSaida(mov, agora));
            boolean aplicaCentavos=centavos.novaRegra(org,id);
            var snapshot=aplicaCentavos?PatioCobrancaCentavos.paraCobrar(calculado):null;
            // O evento já tem base/devido congelados na entrada; não arredondar novamente a sua obrigação.
            BigDecimal valor=estadia.isPresent()?calculado:snapshot==null?calculado:snapshot.valorDevido();
            BigDecimal base=aplicaCentavos?(estadia.isPresent() && estadia.get().meioEntrada()==null
                ? estadia.get().centavos().valorCalculado():calculado):null;
            var nova = new Checkout(turno.id(), UUID.randomUUID(), agora,
                    agora.plusMinutes(VALIDADE_PREVIA_MINUTOS), valor, null, null,base);
            caixa.salvarPrevia(org, id, nova);
            return nova;
        });
        long minutos = Math.max(0, Duration.between(mov.getDataEntrada(), previa.calculadoEm()).toMinutes());
        return new Previa(turno.id(), previa.chave(), mov.getVeiculo().getPlaca(), mov.getTipo(), minutos,
                previa.valor(), previa.expiraEm(), estadia.map(PatioEventoService::detalhe).orElse(null),
                previa.valorCalculado(),previa.valorCalculado()==null?null:previa.valor(),
                previa.valorCalculado()==null?null:previa.valor().subtract(previa.valorCalculado()));
    }

    @Transactional
    public Comprovante saida(UsuarioAutenticado ator, long id, long turnoId, UUID chave, Meio meio) {
        return saida(ator,id,turnoId,chave,meio,null);
    }

    @Transactional
    public Comprovante saida(UsuarioAutenticado ator, long id, long turnoId, UUID chave, Meio meio,BigDecimal recebido) {
        return saida(ator, id, turnoId, chave, meio, recebido, null);
    }

    @Transactional
    public Comprovante saida(UsuarioAutenticado ator, long id, long turnoId, UUID chave, Meio meio,
                              BigDecimal recebido, PatioTomador informado) {
        long org = ator.organizacaoId();
        PatioTomador tomador = PatioTomador.normalizar(informado);
        Turno turno = turnoDoOperador(ator, turnoId);
        var mov = movimentos.buscarParaAlterar(org, id);
        var previa = caixa.checkout(org, id)
                .filter(p -> p.turnoId() == turno.id() && Objects.equals(p.chave(), chave))
                .orElseThrow(PatioCaixaService::previaInvalida);
        var dinheiro=PatioDinheiro.conferir(meio,previa.valor(),recebido);
        var estadia = estadias.buscar(org, id);
        boolean pagoNaSaida = previa.valor().signum() > 0 && meio != Meio.SEM_COBRANCA
                && estadia.filter(e -> e.meioEntrada() == null).isPresent();
        if (tomador != null && !pagoNaSaida) throw tomadorForaDoPagamento();
        if (previa.confirmadoEm() != null) {
            if (previa.meio() != meio) throw new ConflitoException("A saída já foi registrada com outro meio.");
            if (pagoNaSaida && !Objects.equals(estadia.get().tomadorPagamento(), tomador)) {
                throw new ConflitoException("A saída já foi registrada com outra identificação fiscal.");
            }
            var original=operacao.pagamento(org,id);
            if(dinheiro.recebido()!=null && original.isPresent() && original.get().recebido()!=null
                && dinheiro.recebido().compareTo(original.get().recebido())!=0)
                throw new ConflitoException("A saída já recebeu outro valor em dinheiro.");
            return comprovante(org, mov, previa, meio, previa.confirmadoEm());
        }
        if (turno.fechadoEm() != null) throw new ConflitoException("Este turno já foi fechado.");
        exigirMovimentoAberto(mov);
        var agora = OffsetDateTime.now();
        if (!previa.expiraEm().isAfter(agora)) throw previaInvalida();
        if ((previa.valor().signum() == 0) != (meio == Meio.SEM_COBRANCA)) {
            throw new ConflitoException("O meio de pagamento precisa corresponder ao valor da saída.");
        }
        if (tomador != null) estadias.registrarTomadorNaSaida(org, id, tomador);
        caixa.confirmar(org, id, agora, meio);
        operacao.dinheiro(org,id,"SAIDA",turnoId,dinheiro);
        if(previa.valorCalculado()!=null && estadia.isEmpty())
            centavos.confirmarComum(org,id,new PatioCobrancaCentavos.Valor(previa.valorCalculado(),previa.valor(),
                previa.valor().subtract(previa.valorCalculado())));
        movimentos.fecharMovimentacao(mov, agora, estadia.map(PatioEventoRepository.Estadia::valor).orElse(previa.valor()));
        estadia.ifPresent(e -> eventos.encerrar(org, e, agora));
        if (tomador != null) clientesTomadores.cadastrarSeNovo(org, tomador.documento(), tomador.nome());
        auditar(org, "PATIO_SAIDA_CONFIRMADA", "MOVIMENTACAO", id, Map.of(
                "turnoId", turnoId, "valor", previa.valor(), "meio", meio.name()));
        return comprovante(org, mov, previa, meio, agora);
    }

    private Comprovante comprovante(long org, Movimentacao mov, Checkout previa, Meio meio, OffsetDateTime instante) {
        var pago=operacao.pagamento(org,mov.getId()).filter(p -> p.fase().equals("SAIDA"));
        return new Comprovante(mov.getId(), previa.turnoId(), mov.getVeiculo().getPlaca(), previa.valor(), meio, instante,
                estadias.buscar(org, mov.getId()).map(PatioEventoService::detalhe).orElse(null),
                pago.map(PatioOperacaoRepository.Pagamento::recebido).orElse(null),
                pago.map(PatioOperacaoRepository.Pagamento::troco).orElse(null),mov.getStatus()==StatusMovimentacao.CANCELADO,
                previa.valorCalculado(),previa.valorCalculado()==null?null:previa.valor(),
                previa.valorCalculado()==null?null:previa.valor().subtract(previa.valorCalculado()));
    }

    private static CampoInvalidoException tomadorForaDoPagamento() {
        return new CampoInvalidoException("tomador",
                "A identificação fiscal é informada apenas ao receber o pagamento do evento.");
    }

    private Turno turnoAberto(UsuarioAutenticado ator) {
        return caixa.aberto(ator.organizacaoId(), ator.userId(), true)
                .orElseThrow(() -> new ConflitoException("Abra o turno para operar o pátio."));
    }

    private Turno turnoDoOperador(UsuarioAutenticado ator, long id) {
        return caixa.bloquear(ator.organizacaoId(), ator.userId(), id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turno do pátio", id));
    }

    private static void exigirMovimentoAberto(Movimentacao mov) {
        if (mov.getStatus() != StatusMovimentacao.ABERTO) throw new ConflitoException("O veículo não está mais no pátio.");
    }

    private static ConflitoException previaInvalida() {
        return new ConflitoException("A prévia não está mais válida. Confira o valor atualizado antes de cobrar.");
    }

    Resumo resumo(long org, Turno t) {
        var totais = caixa.totais(org, t.id());
        var movimentos=movimentosDinheiro.totais(org,t.id());
        var esperado = t.fundoTroco().add(totais.dinheiro()).add(movimentos.suprimentos()).subtract(movimentos.sangrias());
        return new Resumo(t.id(), t.abertoEm(), t.fechadoEm(), t.fundoTroco(), totais.saidas(),
                totais.dinheiro(), totais.pix(), totais.cartao(),
                totais.dinheiro().add(totais.pix()).add(totais.cartao()), esperado, t.dinheiroContado(),
                t.dinheiroContado() == null ? null : t.dinheiroContado().subtract(esperado),
                movimentos.suprimentos(),movimentos.sangrias());
    }

    private void auditar(long org, String acao, String recurso, long id, Map<String, Object> dados) {
        auditoria.registrar(new RegistrarAuditoriaCommand(org, acao, recurso, id, null,
                null, null, null, dados, null, null, ClasseRetencaoAuditoria.OPERACIONAL));
    }
}
