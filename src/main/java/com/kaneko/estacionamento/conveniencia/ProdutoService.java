package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.conveniencia.dto.ProdutoRequest;
import com.kaneko.estacionamento.conveniencia.dto.ProdutoResponse;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProdutoService {
    private final ProdutoRepository repository;
    private final MarcaRepository marcas;
    private final UnidadeMedidaRepository unidades;
    private final CategoriaRepository categorias;
    private final AuditoriaService auditoria;

    public ProdutoService(ProdutoRepository repository, MarcaRepository marcas,
            UnidadeMedidaRepository unidades, CategoriaRepository categorias,
            AuditoriaService auditoria) {
        this.repository=repository; this.marcas=marcas; this.unidades=unidades;
        this.categorias=categorias; this.auditoria=auditoria;
    }

    @Transactional(readOnly=true)
    public Page<ProdutoResponse> listar(Long org, String nome, Long marcaId,
            Long categoriaId, Boolean ativo, Pageable pageable) {
        Specification<Produto> filtro=FiltroSpecs.todos(
                FiltroSpecs.igual("organizacaoId",org), FiltroSpecs.contemIgnoreCase("nome",nome),
                FiltroSpecs.fkId("marca",marcaId), FiltroSpecs.fkId("categoria",categoriaId),
                FiltroSpecs.igual("ativo",ativo));
        return repository.findAll(filtro,pageable).map(ProdutoResponse::from);
    }

    @Transactional(readOnly=true)
    public Produto buscar(Long org, Long id) {
        return repository.findByIdAndOrganizacaoId(id,org)
                .orElseThrow(()->new RecursoNaoEncontradoException("Produto",id));
    }

    @Transactional
    public Produto criar(Long org, ProdutoRequest req) {
        String codigo=normalizar(req.codigoBarras());
        validarCodigo(org,codigo,null);
        Produto p=new Produto(); p.setOrganizacaoId(org); aplicar(p,org,req,codigo);
        p=repository.saveAndFlush(p); registrar(p,"PRODUTO_CRIADO",null,snapshot(p),null,p.getVersion());
        return p;
    }

    @Transactional
    public Produto atualizar(Long org, Long id, long esperada, ProdutoRequest req) {
        Produto p=buscar(org,id); conferir(esperada,p.getVersion());
        String codigo=normalizar(req.codigoBarras()); validarCodigo(org,codigo,id);
        Map<String,Object> antes=snapshot(p); Long va=p.getVersion();
        aplicar(p,org,req,codigo); p=repository.saveAndFlush(p);
        registrar(p,"PRODUTO_ATUALIZADO",antes,snapshot(p),va,p.getVersion()); return p;
    }

    @Transactional
    public Produto excluir(Long org, Long id, long esperada) {
        Produto p=buscar(org,id); conferir(esperada,p.getVersion());
        Map<String,Object> antes=snapshot(p); Long va=p.getVersion(); p.setAtivo(false);
        repository.flush(); registrar(p,"PRODUTO_DESATIVADO",antes,snapshot(p),va,p.getVersion()); return p;
    }

    private void aplicar(Produto p, Long org, ProdutoRequest r, String codigo) {
        p.setNome(r.nome().trim()); p.setCodigoBarras(codigo); p.setReferencia(r.referencia());
        Marca marca = marcas.findByIdAndOrganizacaoId(r.marcaId(), org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Marca", r.marcaId()));
        if (Boolean.FALSE.equals(marca.getAtivo())) {
            throw new ConflitoException("A marca esta inativa e nao pode ser usada no produto.");
        }
        UnidadeMedida unidade = unidades.findByIdAndOrganizacaoId(r.unidadeMedidaId(), org)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "UnidadeMedida", r.unidadeMedidaId()));
        if (Boolean.FALSE.equals(unidade.getAtivo())) {
            throw new ConflitoException(
                    "A unidade de medida esta inativa e nao pode ser usada no produto.");
        }
        Categoria categoria = r.categoriaId() == null ? null
                : categorias.findByIdAndOrganizacaoId(r.categoriaId(), org)
                        .orElseThrow(() -> new RecursoNaoEncontradoException(
                                "Categoria", r.categoriaId()));
        if (categoria != null && Boolean.FALSE.equals(categoria.getAtivo())) {
            throw new ConflitoException(
                    "A categoria esta inativa e nao pode ser usada no produto.");
        }
        p.setMarca(marca);
        p.setUnidadeMedida(unidade);
        p.setCategoria(categoria);
        p.setValorVenda(r.valorVenda()==null?BigDecimal.ZERO:r.valorVenda());
        p.setQuantidadeMinima(r.quantidadeMinima()==null?BigDecimal.ONE:r.quantidadeMinima());
        p.recalcularLucro(); p.setDescricao(r.descricao()); p.setObservacao(r.observacao());
        if(r.ativo()!=null)p.setAtivo(r.ativo());
    }

    private void validarCodigo(Long org,String codigo,Long id){
        if(codigo!=null&&repository.existeCodigoNormalizado(org,codigo,id))
            throw new ConflitoException("Ja existe um produto com este codigo de barras.");
    }
    private static String normalizar(String v){if(v==null)return null;String x=v.trim();return x.isEmpty()?null:x;}
    private static void conferir(long e,Long a){if(a==null||e!=a)throw new VersaoCatalogoConvenienciaDivergenteException(e,a==null?-1:a);}
    private void registrar(Produto p,String acao,Map<String,Object>a,Map<String,Object>d,Long va,Long vd){
        auditoria.registrar(new RegistrarAuditoriaCommand(p.getOrganizacaoId(),acao,"PRODUTO",p.getId(),null,null,null,a,d,va,vd,ClasseRetencaoAuditoria.CONFIGURACAO));
    }
    private static Map<String,Object> snapshot(Produto p){Map<String,Object>m=new LinkedHashMap<>();m.put("nome",p.getNome());m.put("codigoBarras",p.getCodigoBarras());m.put("referencia",p.getReferencia());m.put("marcaId",p.getMarca().getId());m.put("unidadeMedidaId",p.getUnidadeMedida().getId());m.put("categoriaId",p.getCategoria()==null?null:p.getCategoria().getId());m.put("valorVenda",p.getValorVenda());m.put("percentualLucro",p.getPercentualLucro());m.put("quantidadeMinima",p.getQuantidadeMinima());m.put("descricao",p.getDescricao());m.put("observacao",p.getObservacao());m.put("ativo",p.getAtivo());return m;}
}
