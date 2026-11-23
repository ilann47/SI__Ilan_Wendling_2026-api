package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.auditoria.*;
import com.kaneko.estacionamento.common.exception.*;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatioMovimentoDinheiroService {
    public enum Tipo { SUPRIMENTO,SANGRIA }
    public record LinhaTurno(long turnoId,String operador,OffsetDateTime abertoEm,OffsetDateTime fechadoEm,PatioCaixaService.Resumo resumo){}
    public record PaginaTurnos(List<LinhaTurno> content,long totalElements,int pagina,int tamanho){}
    private final PatioCaixaRepository caixa;
    private final PatioCaixaService resumo;
    private final PatioMovimentoDinheiroRepository movimentos;
    private final AuditoriaService auditoria;
    PatioMovimentoDinheiroService(PatioCaixaRepository caixa,PatioCaixaService resumo,
        PatioMovimentoDinheiroRepository movimentos,AuditoriaService auditoria){
        this.caixa=caixa;this.resumo=resumo;this.movimentos=movimentos;this.auditoria=auditoria;
    }
    static void exigirSaldo(Tipo tipo,BigDecimal valor,BigDecimal saldo){
        if(tipo==null || valor==null || valor.signum()<=0)throw new ConflitoException("Informe um valor positivo para o movimento.");
        try{valor.setScale(6,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw new ConflitoException("Use até seis casas decimais.");}
        if(tipo==Tipo.SANGRIA && valor.compareTo(saldo)>0)throw new ConflitoException("A sangria não pode superar o dinheiro disponível neste turno.");
    }
    @Transactional
    public PatioCaixaService.Resumo registrar(UsuarioAutenticado ator,long turnoId,UUID chave,Tipo tipo,BigDecimal valor,String motivo){
        long org=ator.organizacaoId();
        if(chave==null || motivo==null || motivo.strip().length()<10 || motivo.strip().length()>300)
            throw new ConflitoException("Informe a confirmação e um motivo de 10 a 300 caracteres.");
        String razao=motivo.strip();
        var turno=caixa.bloquear(org,ator.userId(),turnoId)
            .orElseThrow(()->new RecursoNaoEncontradoException("Turno do pátio",turnoId));
        movimentos.bloquearChave(org,chave);
        var anterior=movimentos.porChave(org,chave);
        if(anterior.isPresent()){
            var m=anterior.get();
            if(m.turnoId()!=turnoId || m.usuarioId()!=ator.userId() || m.tipo()!=tipo || valor==null
                || m.valor().compareTo(valor)!=0 || !m.motivo().equals(razao))
                throw new ConflitoException("Esta confirmação já foi usada com outro movimento de dinheiro.");
            return resumo.resumo(org,turno);
        }
        if(turno.fechadoEm()!=null)throw new ConflitoException("Este turno já foi fechado.");
        var antes=resumo.resumo(org,turno);
        exigirSaldo(tipo,valor,antes.dinheiroEsperado());
        var movimento=movimentos.inserir(org,turnoId,ator.userId(),chave,tipo,valor.setScale(6,RoundingMode.UNNECESSARY),razao);
        auditoria.registrar(new RegistrarAuditoriaCommand(org,"PATIO_"+tipo.name(),"PATIO_TURNO",turnoId,null,
            null,razao,null,Map.of("movimentoId",movimento.id(),"valor",valor,"saldoAnterior",antes.dinheiroEsperado()),
            null,null,ClasseRetencaoAuditoria.OPERACIONAL));
        return resumo.resumo(org,turno);
    }
    @Transactional(readOnly=true)
    public List<PatioMovimentoDinheiroRepository.Movimento> historico(UsuarioAutenticado ator,long id,boolean supervisor){
        long org=ator.organizacaoId();
        var turno=supervisor?movimentos.turno(org,id):movimentos.turnoDoOperador(org,ator.userId(),id);
        turno.orElseThrow(()->new RecursoNaoEncontradoException("Turno do pátio",id));
        return movimentos.historico(org,id);
    }
    @Transactional(readOnly=true)
    public PaginaTurnos listar(UsuarioAutenticado ator,int pagina,int tamanho){
        if(pagina<0 || tamanho<1 || tamanho>100 || (long)pagina*tamanho>Integer.MAX_VALUE)
            throw new IllegalArgumentException("Informe página válida e tamanho entre 1 e 100.");
        long org=ator.organizacaoId();
        return new PaginaTurnos(movimentos.turnos(org,pagina,tamanho).stream().map(l->new LinhaTurno(l.turno().id(),l.operador(),
            l.turno().abertoEm(),l.turno().fechadoEm(),resumo.resumo(org,l.turno()))).toList(),movimentos.quantidade(org),pagina,tamanho);
    }
}
