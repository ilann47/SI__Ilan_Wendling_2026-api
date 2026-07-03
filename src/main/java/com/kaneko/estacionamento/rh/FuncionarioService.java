package com.kaneko.estacionamento.rh;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.util.Documentos;
import com.kaneko.estacionamento.geografia.Cidade;
import com.kaneko.estacionamento.geografia.CidadeRepository;
import com.kaneko.estacionamento.geografia.Pais;
import com.kaneko.estacionamento.geografia.PaisRepository;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.pessoas.TipoPessoa;
import com.kaneko.estacionamento.rh.dto.FuncionarioRequest;
import com.kaneko.estacionamento.rh.dto.FuncionarioResponse;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Funcionario.
 *
 * <p>Garante a unicidade do CPF, exige um {@link Cargo} existente e valida as
 * referencias opcionais a cidade e nacionalidade (pais).
 */
@Service
public class FuncionarioService {

    private final FuncionarioRepository repository;
    private final CargoRepository cargoRepository;
    private final CidadeRepository cidadeRepository;
    private final PaisRepository paisRepository;

    public FuncionarioService(FuncionarioRepository repository,
                              CargoRepository cargoRepository,
                              CidadeRepository cidadeRepository,
                              PaisRepository paisRepository) {
        this.repository = repository;
        this.cargoRepository = cargoRepository;
        this.cidadeRepository = cidadeRepository;
        this.paisRepository = paisRepository;
    }

    @Transactional(readOnly = true)
    public Page<FuncionarioResponse> listar(String nome, Long cargoId, Boolean ativo, Pageable pageable) {
        Specification<Funcionario> filtro = FiltroSpecs.todos(
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.fkId("cargo", cargoId),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(FuncionarioResponse::from);
    }

    @Transactional(readOnly = true)
    public Funcionario buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Funcionario", id));
    }

    @Transactional
    public Funcionario criar(FuncionarioRequest req) {
        String cpf = Documentos.soDigitos(req.cpf());
        if (repository.existsByCpf(cpf)) {
            throw new ConflitoException("Ja existe um funcionario cadastrado com este CPF.");
        }
        Funcionario funcionario = new Funcionario();
        aplicar(funcionario, req, cpf);
        return repository.save(funcionario);
    }

    @Transactional
    public Funcionario atualizar(Long id, FuncionarioRequest req) {
        Funcionario funcionario = buscar(id);
        String cpf = Documentos.soDigitos(req.cpf());
        if (repository.existsByCpfAndIdNot(cpf, id)) {
            throw new ConflitoException("Ja existe um funcionario cadastrado com este CPF.");
        }
        aplicar(funcionario, req, cpf);
        return repository.save(funcionario);
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).setAtivo(false);
    }

    private void aplicar(Funcionario f, FuncionarioRequest req, String cpf) {
        f.setNome(req.nome().trim());
        f.setApelido(req.apelido());
        f.setTipo(req.tipo() != null ? req.tipo() : TipoPessoa.FISICA);
        f.setCpf(cpf);
        f.setRgInscricaoEstadual(req.rgInscricaoEstadual());
        f.setDataNascimento(req.dataNascimento());
        f.setSexo(req.sexo());
        f.setEstadoCivil(req.estadoCivil());
        f.setTelefone(req.telefone());
        f.setEmail(req.email());
        f.setEndereco(req.endereco());
        f.setNumero(req.numero());
        f.setComplemento(req.complemento());
        f.setBairro(req.bairro());
        f.setCep(req.cep());
        f.setCidade(req.cidadeId() != null ? buscarCidade(req.cidadeId()) : null);
        f.setNacionalidade(req.nacionalidadeId() != null ? buscarPais(req.nacionalidadeId()) : null);
        f.setCargo(buscarCargo(req.cargoId()));
        f.setCnh(req.cnh());
        f.setDataValidadeCnh(req.dataValidadeCnh());
        f.setDataAdmissao(req.dataAdmissao() != null ? req.dataAdmissao() : LocalDate.now());
        f.setDataDemissao(req.dataDemissao());
        f.setSalario(req.salario());
        f.setObservacao(req.observacao());
        if (req.ativo() != null) {
            f.setAtivo(req.ativo());
        }
    }

    private Cargo buscarCargo(Long cargoId) {
        return cargoRepository.findById(cargoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cargo", cargoId));
    }

    private Cidade buscarCidade(Long cidadeId) {
        return cidadeRepository.findById(cidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade", cidadeId));
    }

    private Pais buscarPais(Long paisId) {
        return paisRepository.findById(paisId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pais", paisId));
    }
}
