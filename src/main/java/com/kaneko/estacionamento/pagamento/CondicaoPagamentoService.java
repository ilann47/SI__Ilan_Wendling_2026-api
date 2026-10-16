package com.kaneko.estacionamento.pagamento;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.pagamento.dto.CondicaoPagamentoRequest;
import com.kaneko.estacionamento.pagamento.dto.CondicaoPagamentoResponse;
import com.kaneko.estacionamento.pagamento.dto.ParcelaRequest;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio da condicao de pagamento (agregado com parcelas).
 *
 * <p>Garante a unicidade do nome e, quando o detalhamento de parcelas e
 * informado, valida que a soma dos percentuais e 100 e que a quantidade de
 * parcelas confere com {@code numeroParcelas}. As formas de pagamento
 * referenciadas pelas parcelas sao validadas. A sincronizacao das parcelas e
 * feita mutando a colecao (clear/add) para preservar o {@code orphanRemoval}.
 */
@Service
public class CondicaoPagamentoService {

    private static final BigDecimal CEM = new BigDecimal("100.00");

    private final CondicaoPagamentoRepository repository;
    private final FormaPagamentoRepository formaPagamentoRepository;
    private final AuditoriaService auditoria;

    public CondicaoPagamentoService(CondicaoPagamentoRepository repository,
                                    FormaPagamentoRepository formaPagamentoRepository,
                                    AuditoriaService auditoria) {
        this.repository = repository;
        this.formaPagamentoRepository = formaPagamentoRepository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<CondicaoPagamentoResponse> listar(
            Long organizacaoId, String nome, Boolean ativo, Pageable pageable) {
        Specification<CondicaoPagamento> filtro = FiltroSpecs.todos(
                FiltroSpecs.igual("organizacaoId", organizacaoId),
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(CondicaoPagamentoResponse::from);
    }

    @Transactional(readOnly = true)
    public CondicaoPagamento buscar(Long organizacaoId, Long id) {
        return repository.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", id));
    }

    @Transactional
    public CondicaoPagamento criar(Long organizacaoId, CondicaoPagamentoRequest req) {
        String nome = req.nome().trim();
        if (repository.existeNomeNormalizado(organizacaoId, nome, null)) {
            throw new ConflitoException("Ja existe uma condicao de pagamento com este nome.");
        }
        validarParcelas(req);
        CondicaoPagamento condicao = new CondicaoPagamento();
        condicao.setOrganizacaoId(organizacaoId);
        aplicar(condicao, req, nome);
        sincronizarParcelas(organizacaoId, condicao, req.parcelas());
        condicao = repository.saveAndFlush(condicao);
        registrar(condicao, "CONDICAO_PAGAMENTO_CRIADA", null, snapshot(condicao));
        return condicao;
    }

    @Transactional
    public CondicaoPagamento atualizar(
            Long organizacaoId, Long id, CondicaoPagamentoRequest req) {
        CondicaoPagamento condicao = buscar(organizacaoId, id);
        String nome = req.nome().trim();
        if (repository.existeNomeNormalizado(organizacaoId, nome, id)) {
            throw new ConflitoException("Ja existe uma condicao de pagamento com este nome.");
        }
        Map<String, Object> anterior = snapshot(condicao);
        validarParcelas(req);
        aplicar(condicao, req, nome);
        sincronizarParcelas(organizacaoId, condicao, req.parcelas());
        condicao = repository.saveAndFlush(condicao);
        registrar(condicao, "CONDICAO_PAGAMENTO_ATUALIZADA", anterior, snapshot(condicao));
        return condicao;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        CondicaoPagamento condicao = buscar(organizacaoId, id);
        Map<String, Object> anterior = snapshot(condicao);
        condicao.setAtivo(false);
        repository.flush();
        registrar(condicao, "CONDICAO_PAGAMENTO_DESATIVADA", anterior, snapshot(condicao));
    }

    private void aplicar(CondicaoPagamento condicao, CondicaoPagamentoRequest req, String nome) {
        condicao.setNome(nome);
        condicao.setNumeroParcelas(req.numeroParcelas());
        condicao.setPercentualJuros(req.percentualJuros() != null ? req.percentualJuros() : BigDecimal.ZERO);
        condicao.setPercentualMulta(req.percentualMulta() != null ? req.percentualMulta() : BigDecimal.ZERO);
        condicao.setPercentualDesconto(req.percentualDesconto() != null ? req.percentualDesconto() : BigDecimal.ZERO);
        if (req.ativo() != null) {
            condicao.setAtivo(req.ativo());
        }
    }

    /** Valida o detalhamento de parcelas, quando informado (soma 100% e quantidade coerente). */
    private void validarParcelas(CondicaoPagamentoRequest req) {
        List<ParcelaRequest> parcelas = req.parcelas();
        if (parcelas == null || parcelas.isEmpty()) {
            return;
        }
        if (parcelas.size() != req.numeroParcelas()) {
            throw new ConflitoException("A quantidade de parcelas detalhadas deve ser igual ao número de parcelas.");
        }
        BigDecimal soma = parcelas.stream()
                .map(ParcelaRequest::percentual)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (soma.compareTo(CEM) != 0) {
            throw new ConflitoException("A soma dos percentuais das parcelas deve ser 100.");
        }
    }

    /** Recria as parcelas mutando a colecao do agregado (preserva orphanRemoval). */
    private void sincronizarParcelas(
            Long organizacaoId,
            CondicaoPagamento condicao,
            List<ParcelaRequest> parcelas) {
        condicao.getParcelas().clear();
        if (parcelas == null) {
            return;
        }
        for (ParcelaRequest pr : parcelas) {
            ParcelaCondicaoPagamento parcela = new ParcelaCondicaoPagamento();
            parcela.setOrganizacaoId(organizacaoId);
            parcela.setCondicaoPagamento(condicao);
            parcela.setNumero(pr.numero());
            parcela.setDias(pr.dias());
            parcela.setPercentual(pr.percentual());
            parcela.setFormaPagamento(pr.formaPagamentoId() != null
                    ? buscarForma(organizacaoId, pr.formaPagamentoId()) : null);
            condicao.getParcelas().add(parcela);
        }
    }

    private FormaPagamento buscarForma(Long organizacaoId, Long formaPagamentoId) {
        return formaPagamentoRepository.findByIdAndOrganizacaoId(formaPagamentoId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("FormaPagamento", formaPagamentoId));
    }

    private void registrar(
            CondicaoPagamento condicao,
            String acao,
            Map<String, Object> anterior,
            Map<String, Object> posterior) {
        auditoria.registrar(new RegistrarAuditoriaCommand(
                condicao.getOrganizacaoId(), acao, "CONDICAO_PAGAMENTO", condicao.getId(),
                null, null, null, anterior, posterior, null, null,
                ClasseRetencaoAuditoria.CONFIGURACAO));
    }

    private static Map<String, Object> snapshot(CondicaoPagamento condicao) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("nome", condicao.getNome());
        estado.put("numeroParcelas", condicao.getNumeroParcelas());
        estado.put("percentualJuros", condicao.getPercentualJuros());
        estado.put("percentualMulta", condicao.getPercentualMulta());
        estado.put("percentualDesconto", condicao.getPercentualDesconto());
        estado.put("ativo", condicao.getAtivo());
        estado.put("parcelas", condicao.getParcelas().stream()
                .map(CondicaoPagamentoService::snapshotParcela)
                .toList());
        return estado;
    }

    private static Map<String, Object> snapshotParcela(ParcelaCondicaoPagamento parcela) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("numero", parcela.getNumero());
        estado.put("dias", parcela.getDias());
        estado.put("percentual", parcela.getPercentual());
        estado.put("formaPagamentoId", parcela.getFormaPagamento() == null
                ? null : parcela.getFormaPagamento().getId());
        return estado;
    }
}
