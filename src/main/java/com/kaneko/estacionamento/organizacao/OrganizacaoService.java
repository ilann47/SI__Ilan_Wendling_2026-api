package com.kaneko.estacionamento.organizacao;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.util.Documentos;
import com.kaneko.estacionamento.organizacao.dto.AtualizarOrganizacaoRequest;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.organizacao.dto.OrganizacaoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizacaoService {

    private final OrganizacaoRepository repository;

    public OrganizacaoService(OrganizacaoRepository repository) {
        this.repository = repository;
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

        organizacao.atualizarCadastro(
                valorOuAtual(request.getRazaoSocial(), organizacao.getRazaoSocial()),
                request.getNomeFantasia() != null ? request.getNomeFantasia() : organizacao.getNomeFantasia(),
                valorOuAtual(request.getMoeda(), organizacao.getMoeda()),
                valorOuAtual(request.getFusoHorario(), organizacao.getFusoHorario()),
                valorOuAtual(request.getRegiao(), organizacao.getRegiao()),
                valorOuAtual(request.getPlano(), organizacao.getPlano()));
        repository.flush();
        return OrganizacaoResponse.from(organizacao);
    }

    private Organizacao buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Organizacao", id));
    }

    private static String valorOuAtual(String novoValor, String atual) {
        return novoValor != null ? novoValor : atual;
    }
}
