package com.kaneko.estacionamento.organizacao;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.util.Documentos;
import com.kaneko.estacionamento.organizacao.dto.AtualizarOrganizacaoRequest;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.organizacao.dto.OrganizacaoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OrganizacaoService {

    private final OrganizacaoRepository repository;
    private final AuditoriaService auditoria;

    public OrganizacaoService(OrganizacaoRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    @Transactional
    public OrganizacaoResponse criar(CriarOrganizacaoRequest request) {
        Organizacao organizacao = Organizacao.provisionar(
                Documentos.soDigitos(request.documento()),
                request.razaoSocial(),
                request.nomeFantasia(),
                request.moeda(),
                request.fusoHorario(),
                request.regiao(),
                request.plano());
        if (repository.existsByDocumento(organizacao.getDocumento())) {
            throw new ConflitoException("Ja existe uma organizacao com este documento.");
        }
        Organizacao persistida = repository.saveAndFlush(organizacao);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                persistida.getId(), "ORGANIZACAO_CRIADA", "ORGANIZACAO", persistida.getId(),
                null, null, null, null, snapshot(persistida), null, persistida.getVersion(),
                ClasseRetencaoAuditoria.CONFIGURACAO));
        return OrganizacaoResponse.from(persistida);
    }

    @Transactional(readOnly = true)
    public OrganizacaoResponse buscar(Long id) {
        return OrganizacaoResponse.from(buscarEntidade(id));
    }

    @Transactional
    public OrganizacaoResponse atualizar(Long id, long versaoEsperada, AtualizarOrganizacaoRequest request) {
        Organizacao organizacao = buscarEntidade(id);
        if (organizacao.getVersion() != versaoEsperada) {
            throw new VersaoOrganizacaoDivergenteException(versaoEsperada, organizacao.getVersion());
        }
        Map<String, Object> anterior = snapshot(organizacao);
        Long versaoAnterior = organizacao.getVersion();

        organizacao.atualizarCadastro(
                valorOuAtual(request.getRazaoSocial(), organizacao.getRazaoSocial()),
                request.getNomeFantasia() != null ? request.getNomeFantasia() : organizacao.getNomeFantasia(),
                valorOuAtual(request.getMoeda(), organizacao.getMoeda()),
                valorOuAtual(request.getFusoHorario(), organizacao.getFusoHorario()),
                valorOuAtual(request.getRegiao(), organizacao.getRegiao()),
                valorOuAtual(request.getPlano(), organizacao.getPlano()));
        repository.flush();
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacao.getId(), "ORGANIZACAO_ATUALIZADA", "ORGANIZACAO", organizacao.getId(),
                null, null, null, anterior, snapshot(organizacao), versaoAnterior, organizacao.getVersion(),
                ClasseRetencaoAuditoria.CONFIGURACAO));
        return OrganizacaoResponse.from(organizacao);
    }

    private Organizacao buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Organizacao", id));
    }

    private static String valorOuAtual(String novoValor, String atual) {
        return novoValor != null ? novoValor : atual;
    }

    private static Map<String, Object> snapshot(Organizacao organizacao) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("razaoSocial", organizacao.getRazaoSocial());
        if (organizacao.getNomeFantasia() != null) estado.put("nomeFantasia", organizacao.getNomeFantasia());
        estado.put("moeda", organizacao.getMoeda());
        estado.put("fusoHorario", organizacao.getFusoHorario());
        estado.put("regiao", organizacao.getRegiao());
        estado.put("plano", organizacao.getPlano());
        estado.put("status", organizacao.getStatus().name());
        return estado;
    }
}
