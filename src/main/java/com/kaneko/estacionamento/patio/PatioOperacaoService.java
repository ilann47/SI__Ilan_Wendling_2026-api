package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.auditoria.*;
import com.kaneko.estacionamento.common.exception.*;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.ordensservico.OrdemServicoService;
import com.kaneko.estacionamento.patio.dto.VeiculoRequest;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatioOperacaoService {
    public enum Acao { CORRIGIR_PLACA,CANCELAR_ESTADIA,CORRIGIR_MEIO }
    public record Resultado(long movimentacaoId,Acao acao,String placa,String mensagem) {}
    private final PatioOperacaoRepository operacao;
    private final PatioCaixaRepository caixa;
    private final MovimentacaoService movimentos;
    private final MovimentacaoRepository repositorio;
    private final VeiculoRepository veiculos;
    private final VeiculoService cadastro;
    private final PatioEventoRepository estadias;
    private final PatioEventoService eventos;
    private final OrdemServicoService ordens;
    private final AuditoriaService auditoria;
    private final PatioCobrancaCentavosRepository centavos;
    PatioOperacaoService(PatioOperacaoRepository operacao,PatioCaixaRepository caixa,MovimentacaoService movimentos,
        MovimentacaoRepository repositorio,VeiculoRepository veiculos,VeiculoService cadastro,
        PatioEventoRepository estadias,PatioEventoService eventos,OrdemServicoService ordens,AuditoriaService auditoria,
        PatioCobrancaCentavosRepository centavos){
        this.operacao=operacao;this.caixa=caixa;this.movimentos=movimentos;this.repositorio=repositorio;
        this.veiculos=veiculos;this.cadastro=cadastro;this.estadias=estadias;this.eventos=eventos;this.ordens=ordens;this.auditoria=auditoria;this.centavos=centavos;
    }
    @Transactional(readOnly=true)
    public List<PatioOperacaoRepository.VeiculoAberto> buscar(UsuarioAutenticado ator,String placa,Long patio,Long evento){
        return operacao.buscar(ator.organizacaoId(),placa,patio,evento);
    }
    @Transactional(readOnly=true)
    public List<PatioCaixaService.Comprovante> comprovantes(UsuarioAutenticado ator,String placa){
        return operacao.comprovantes(ator.organizacaoId(),placa).stream().map(id->comprovante(ator,id)).toList();
    }
    @Transactional(readOnly=true)
    public PatioCaixaService.Comprovante comprovante(UsuarioAutenticado ator,long id){
        long org=ator.organizacaoId();
        var mov=movimentos.buscar(org,id);
        var pago=operacao.pagamento(org,id).orElseThrow(()->new RecursoNaoEncontradoException("Comprovante",id));
        var meio=operacao.meioEfetivo(org,id,pago.meio());
        BigDecimal recebido=meio==PatioCaixaService.Meio.DINHEIRO && meio==pago.meio() ? pago.recebido():null;
        BigDecimal troco=recebido==null?null:recebido.subtract(pago.valor());
        var snapshot=centavos.snapshot(org,id).orElse(null);
        return new PatioCaixaService.Comprovante(id,pago.turno(),mov.getVeiculo().getPlaca(),pago.valor(),
            meio,pago.instante(),estadias.buscar(org,id).map(PatioEventoService::detalhe).orElse(null),
            recebido,troco,mov.getStatus()==StatusMovimentacao.CANCELADO,
            snapshot==null?null:snapshot.valorCalculado(),snapshot==null?null:snapshot.valorDevido(),
            snapshot==null?null:snapshot.diferencaArredondamento());
    }
    @Transactional
    public Resultado corrigir(UsuarioAutenticado ator,long id,UUID chave,Acao acao,String motivo,String placa,PatioCaixaService.Meio novoMeio){
        long org=ator.organizacaoId();
        String razao=motivo.strip();
        String novaPlaca=acao==Acao.CORRIGIR_PLACA ? PatioPlaca.obrigatoria(placa) : null;
        if(razao.length()<10 || razao.length()>300)throw new ConflitoException("Informe o motivo com 10 a 300 caracteres.");
        if(acao==Acao.CORRIGIR_PLACA){
            if(novoMeio!=null)throw new ConflitoException("Correção de placa não altera o pagamento.");
        }else if(placa!=null)throw new ConflitoException("Informe a placa apenas na correção de placa.");
        if(acao==Acao.CORRIGIR_MEIO && (novoMeio==null || novoMeio==PatioCaixaService.Meio.SEM_COBRANCA))
            throw new ConflitoException("Escolha Dinheiro, Pix ou Cartão.");
        if(acao!=Acao.CORRIGIR_MEIO && novoMeio!=null)throw new ConflitoException("Use a correção de meio para alterar o pagamento.");
        operacao.bloquearChave(org,chave);
        var anterior=operacao.correcao(org,chave);
        if(anterior.isPresent()){
            var c=anterior.get();
            if(c.movimento()!=id || !c.acao().equals(acao.name()) || !c.motivo().equals(razao)
                || !Objects.equals(c.placa(),novaPlaca) || !Objects.equals(c.meio(),novoMeio==null?null:novoMeio.name()))
                throw new ConflitoException("Esta confirmação já foi usada com outros dados.");
            return new Resultado(id,acao,movimentos.buscar(org,id).getVeiculo().getPlaca(),"Correção já registrada. Nenhum lançamento foi duplicado.");
        }
        var turno=caixa.aberto(org,ator.userId(),true).orElseThrow(()->new ConflitoException("Abra seu turno para registrar a correção."));
        var mov=movimentos.buscarParaAlterar(org,id);
        if(mov.getStatus()==StatusMovimentacao.CANCELADO)throw new ConflitoException("Esta estadia já foi cancelada.");
        String velha=mov.getVeiculo().getPlaca();
        var pagamento=operacao.pagamento(org,id);
        var estadia=estadias.buscar(org,id);
        var meioEfetivo=pagamento.map(p->operacao.meioEfetivo(org,id,p.meio())).orElse(null);
        if(acao==Acao.CORRIGIR_PLACA){
            if(mov.getStatus()!=StatusMovimentacao.ABERTO)throw new ConflitoException("Corrija a placa antes de encerrar a estadia.");
            if(mov.getTipo()==TipoMovimentacao.MENSALISTA)throw new ConflitoException("Confira o cadastro do mensalista antes de corrigir a placa.");
            if(PatioPlaca.normalizar(velha).equals(novaPlaca))throw new ConflitoException("A placa informada já está registrada.");
            String normal=novaPlaca;
            var veiculo=veiculos.findByOrganizacaoIdAndPlaca(org,normal)
                .or(()->PatioPlaca.grafiaLegada(normal).flatMap(legada->veiculos.findByOrganizacaoIdAndPlaca(org,legada)))
                .orElseGet(()->cadastro.criar(org,new VeiculoRequest(normal,null,null,null,null,null,true)));
            if(Boolean.FALSE.equals(veiculo.getAtivo()))throw new ConflitoException("O veículo está inativo.");
            if(repositorio.existsByOrganizacaoIdAndVeiculoIdAndStatus(org,veiculo.getId(),StatusMovimentacao.ABERTO))
                throw new ConflitoException("Esta placa já possui uma estadia aberta.");
            mov.setVeiculo(veiculo);
            repositorio.saveAndFlush(mov);
        }else if(acao==Acao.CORRIGIR_MEIO){
            if(pagamento.isEmpty() || pagamento.get().valor().signum()==0)throw new ConflitoException("Não há recebimento para corrigir.");
            if(meioEfetivo==novoMeio)throw new ConflitoException("Este já é o meio de pagamento registrado.");
        }else{
            if(!operacao.fiscalCancelavel(org,id))throw new ConflitoException("Documento fiscal emitido exige tratamento fiscal antes da devolução.");
            if(estadia.isPresent()){
                if(mov.getStatus()==StatusMovimentacao.ABERTO)eventos.liberarEntradaCancelada(org,estadia.get());
                if(estadia.get().ordemServicoId()!=null)ordens.cancelarParaEstadia(org,id,estadia.get().ordemServicoId(),razao);
                operacao.cancelarFiscal(org,id);
            }
            mov.setStatus(StatusMovimentacao.CANCELADO);
            repositorio.saveAndFlush(mov);
        }
        long correcao=operacao.registrar(org,id,ator.userId(),chave,acao.name(),razao,velha,novaPlaca,novoMeio);
        if(acao!=Acao.CORRIGIR_PLACA && pagamento.isPresent() && pagamento.get().valor().signum()>0){
            operacao.lancar(org,id,turno.id(),correcao,meioEfetivo,pagamento.get().valor().negate());
            if(acao==Acao.CORRIGIR_MEIO)operacao.lancar(org,id,turno.id(),correcao,novoMeio,pagamento.get().valor());
        }
        auditoria.registrar(new RegistrarAuditoriaCommand(org,"PATIO_"+acao.name(),"MOVIMENTACAO",id,null,
            null,null,null,Map.of("motivo",razao,"correcaoId",correcao,"turnoId",turno.id()),null,null,ClasseRetencaoAuditoria.OPERACIONAL));
        return new Resultado(id,acao,mov.getVeiculo().getPlaca(),acao==Acao.CANCELAR_ESTADIA
            ? "Estadia cancelada; devolução registrada, origem preservada e pendência fiscal cancelada."
            : "Correção registrada com histórico preservado.");
    }
}
