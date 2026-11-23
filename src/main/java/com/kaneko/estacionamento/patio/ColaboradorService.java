package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.patio.dto.ColaboradorRequest;
import com.kaneko.estacionamento.patio.dto.ColaboradorResponse;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaneko.estacionamento.common.web.FiltroSpecs;

/**
 * Regras de negocio da equipe de colaboradores de um patio: unicidade do e-mail
 * por patio e fluxo de convite (nasce CONVIDADO).
 */
@Service
public class ColaboradorService {

    private final ColaboradorRepository repository;
    private final PatioRepository patioRepository;

    public ColaboradorService(ColaboradorRepository repository, PatioRepository patioRepository) {
        this.repository = repository;
        this.patioRepository = patioRepository;
    }

    @Transactional(readOnly = true)
    public Page<ColaboradorResponse> listar(Long patioId, String nome, Boolean ativo, Pageable pageable) {
        Specification<Colaborador> filtro = FiltroSpecs.todos(
                FiltroSpecs.fkId("patio", patioId),
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(ColaboradorResponse::from);
    }

    @Transactional(readOnly = true)
    public Colaborador buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador", id));
    }

    @Transactional
    public Colaborador criar(ColaboradorRequest req) {
        Patio patio = buscarPatio(req.patioId());
        String email = req.email().trim();
        if (repository.existsByPatioIdAndEmailIgnoreCase(patio.getId(), email)) {
            throw new ConflitoException("Já existe um colaborador com este e-mail neste pátio.");
        }
        Colaborador c = new Colaborador();
        c.setPatio(patio);
        aplicar(c, req, email);
        // Convite: um novo colaborador nasce CONVIDADO (salvo status explicito).
        c.setStatus(req.status() != null ? req.status() : StatusColaborador.CONVIDADO);
        return repository.save(c);
    }

    @Transactional
    public Colaborador atualizar(Long id, ColaboradorRequest req) {
        Colaborador c = buscar(id);
        Patio patio = buscarPatio(req.patioId());
        String email = req.email().trim();
        if (repository.existsByPatioIdAndEmailIgnoreCaseAndIdNot(patio.getId(), email, id)) {
            throw new ConflitoException("Já existe um colaborador com este e-mail neste pátio.");
        }
        c.setPatio(patio);
        aplicar(c, req, email);
        if (req.status() != null) {
            c.setStatus(req.status());
        }
        return repository.save(c);
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).setAtivo(false);
    }

    private void aplicar(Colaborador c, ColaboradorRequest req, String email) {
        c.setNome(req.nome().trim());
        c.setEmail(email);
        c.setPerfil(req.perfil() != null ? req.perfil() : PerfilUsuario.OPERADOR);
        if (req.ativo() != null) {
            c.setAtivo(req.ativo());
        }
    }

    private Patio buscarPatio(Long id) {
        return patioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Patio", id));
    }
}
