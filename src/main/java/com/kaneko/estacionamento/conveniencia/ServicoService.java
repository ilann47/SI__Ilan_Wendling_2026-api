package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.auditoria.*;
import com.kaneko.estacionamento.common.exception.*;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.conveniencia.dto.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoService {
 private final ServicoRepository repository; private final AuditoriaService auditoria;
 public ServicoService(ServicoRepository r,AuditoriaService a){repository=r;auditoria=a;}
 @Transactional(readOnly=true) public Page<ServicoResponse> listar(Long org,String nome,Boolean ativo,Pageable p){
  Specification<Servico> f=FiltroSpecs.todos(FiltroSpecs.igual("organizacaoId",org),FiltroSpecs.contemIgnoreCase("nome",nome),FiltroSpecs.igual("ativo",ativo));return repository.findAll(f,p).map(ServicoResponse::from);}
 @Transactional(readOnly=true) public Servico buscar(Long org,Long id){return repository.findByIdAndOrganizacaoId(id,org).orElseThrow(()->new RecursoNaoEncontradoException("Servico",id));}
 @Transactional public Servico criar(Long org,ServicoRequest r){String n=r.nome().trim();validar(org,n,null);Servico s=new Servico();s.setOrganizacaoId(org);aplicar(s,r,n);s=repository.saveAndFlush(s);reg(s,"SERVICO_CRIADO",null,snap(s),null,s.getVersion());return s;}
 @Transactional public Servico atualizar(Long org,Long id,long e,ServicoRequest r){Servico s=buscar(org,id);conf(e,s.getVersion());String n=r.nome().trim();validar(org,n,id);Map<String,Object>a=snap(s);Long va=s.getVersion();aplicar(s,r,n);s=repository.saveAndFlush(s);reg(s,"SERVICO_ATUALIZADO",a,snap(s),va,s.getVersion());return s;}
 @Transactional public Servico excluir(Long org,Long id,long e){Servico s=buscar(org,id);conf(e,s.getVersion());Map<String,Object>a=snap(s);Long va=s.getVersion();s.setAtivo(false);repository.flush();reg(s,"SERVICO_DESATIVADO",a,snap(s),va,s.getVersion());return s;}
 private void validar(Long o,String n,Long i){if(repository.existeNomeNormalizado(o,n,i))throw new ConflitoException("Ja existe um servico cadastrado com este nome.");}
 private static void aplicar(Servico s,ServicoRequest r,String n){s.setNome(n);s.setDescricao(r.descricao());s.setValorPadrao(r.valorPadrao());if(r.ativo()!=null)s.setAtivo(r.ativo());}
 private static void conf(long e,Long a){if(a==null||e!=a)throw new VersaoCatalogoConvenienciaDivergenteException(e,a==null?-1:a);}
 private void reg(Servico s,String ac,Map<String,Object>a,Map<String,Object>d,Long va,Long vd){auditoria.registrar(new RegistrarAuditoriaCommand(s.getOrganizacaoId(),ac,"SERVICO",s.getId(),null,null,null,a,d,va,vd,ClasseRetencaoAuditoria.CONFIGURACAO));}
 private static Map<String,Object> snap(Servico s){Map<String,Object>m=new LinkedHashMap<>();m.put("nome",s.getNome());m.put("descricao",s.getDescricao());m.put("valorPadrao",s.getValorPadrao());m.put("ativo",s.getAtivo());return m;}
}
