package com.kaneko.estacionamento.rh;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.util.Documentos;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.geografia.Cidade;
import com.kaneko.estacionamento.geografia.CidadeRepository;
import com.kaneko.estacionamento.pessoas.TipoPessoa;
import com.kaneko.estacionamento.rh.dto.FuncionarioRequest;
import com.kaneko.estacionamento.rh.dto.FuncionarioResponse;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regras tenant-aware do cadastro de Funcionario. */
@Service
public class FuncionarioService {

    private final FuncionarioRepository repository;
    private final CargoRepository cargoRepository;
    private final CidadeRepository cidadeRepository;
    private final AuditoriaService auditoria;

    public FuncionarioService(
            FuncionarioRepository repository,
            CargoRepository cargoRepository,
            CidadeRepository cidadeRepository,
            AuditoriaService auditoria) {
        this.repository = repository;
        this.cargoRepository = cargoRepository;
        this.cidadeRepository = cidadeRepository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<FuncionarioResponse> listar(
            Long organizacaoId,
            String nome,
            Long cargoId,
            Boolean ativo,
            Pageable pageable) {
        Specification<Funcionario> filtro = FiltroSpecs.todos(
                FiltroSpecs.igual("organizacaoId", organizacaoId),
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.fkId("cargo", cargoId),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(FuncionarioResponse::from);
    }

    @Transactional(readOnly = true)
    public Funcionario buscar(Long organizacaoId, Long id) {
        return repository.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Funcionario", id));
    }

    @Transactional
    public Funcionario criar(Long organizacaoId, FuncionarioRequest req) {
        String cpf = Documentos.soDigitos(req.cpf());
        if (repository.existeCpf(organizacaoId, cpf, null)) {
            throw new ConflitoException("Ja existe um funcionario cadastrado com este CPF.");
        }
        Funcionario funcionario = new Funcionario();
        funcionario.setOrganizacaoId(organizacaoId);
        aplicar(organizacaoId, funcionario, req, cpf);
        funcionario = repository.saveAndFlush(funcionario);
        registrar(funcionario, "FUNCIONARIO_CRIADO", null, snapshot(funcionario));
        return funcionario;
    }

    @Transactional
    public Funcionario atualizar(Long organizacaoId, Long id, FuncionarioRequest req) {
        Funcionario funcionario = buscar(organizacaoId, id);
        String cpf = Documentos.soDigitos(req.cpf());
        if (repository.existeCpf(organizacaoId, cpf, id)) {
            throw new ConflitoException("Ja existe um funcionario cadastrado com este CPF.");
        }
        Map<String, Object> anterior = snapshot(funcionario);
        aplicar(organizacaoId, funcionario, req, cpf);
        funcionario = repository.saveAndFlush(funcionario);
        registrar(funcionario, "FUNCIONARIO_ATUALIZADO", anterior, snapshot(funcionario));
        return funcionario;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        Funcionario funcionario = buscar(organizacaoId, id);
        Map<String, Object> anterior = snapshot(funcionario);
        funcionario.setAtivo(false);
        repository.flush();
        registrar(funcionario, "FUNCIONARIO_DESATIVADO", anterior, snapshot(funcionario));
    }

    private void aplicar(
            Long organizacaoId,
            Funcionario funcionario,
            FuncionarioRequest req,
            String cpf) {
        funcionario.setNome(req.nome().trim());
        funcionario.setApelido(req.apelido());
        funcionario.setTipo(req.tipo() != null ? req.tipo() : TipoPessoa.FISICA);
        funcionario.setCpf(cpf);
        funcionario.setRgInscricaoEstadual(req.rgInscricaoEstadual());
        funcionario.setDataNascimento(req.dataNascimento());
        funcionario.setSexo(req.sexo());
        funcionario.setEstadoCivil(req.estadoCivil());
        funcionario.setTelefone(req.telefone());
        funcionario.setEmail(req.email());
        funcionario.setEndereco(req.endereco());
        funcionario.setNumero(req.numero());
        funcionario.setComplemento(req.complemento());
        funcionario.setBairro(req.bairro());
        funcionario.setCep(req.cep());
        funcionario.setCidade(req.cidadeId() != null ? buscarCidade(req.cidadeId()) : null);
        funcionario.setCargo(buscarCargo(organizacaoId, req.cargoId()));
        funcionario.setCnh(req.cnh());
        funcionario.setDataValidadeCnh(req.dataValidadeCnh());
        funcionario.setDataAdmissao(
                req.dataAdmissao() != null ? req.dataAdmissao() : LocalDate.now());
        funcionario.setDataDemissao(req.dataDemissao());
        funcionario.setSalario(req.salario());
        funcionario.setObservacao(req.observacao());
        if (req.ativo() != null) {
            funcionario.setAtivo(req.ativo());
        }
    }

    private Cargo buscarCargo(Long organizacaoId, Long cargoId) {
        return cargoRepository.findByIdAndOrganizacaoId(cargoId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cargo", cargoId));
    }

    private Cidade buscarCidade(Long cidadeId) {
        return cidadeRepository.findById(cidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade", cidadeId));
    }

    private void registrar(
            Funcionario funcionario,
            String acao,
            Map<String, Object> anterior,
            Map<String, Object> posterior) {
        auditoria.registrar(new RegistrarAuditoriaCommand(
                funcionario.getOrganizacaoId(), acao, "FUNCIONARIO", funcionario.getId(),
                null, null, null, anterior, posterior, null, null,
                ClasseRetencaoAuditoria.RH));
    }

    private static Map<String, Object> snapshot(Funcionario funcionario) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("cargoId", funcionario.getCargo().getId());
        estado.put("ativo", funcionario.getAtivo());
        return estado;
    }
}
