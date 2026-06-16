package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.patio.dto.VeiculoRequest;
import com.kaneko.estacionamento.patio.dto.VeiculoResponse;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Veiculo.
 *
 * <p>Garante a unicidade da placa (normalizada para maiusculas) e valida o
 * cliente referenciado quando informado.
 */
@Service
public class VeiculoService {

    private final VeiculoRepository repository;
    private final ClienteRepository clienteRepository;

    public VeiculoService(VeiculoRepository repository, ClienteRepository clienteRepository) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
    }

    @Transactional(readOnly = true)
    public Page<VeiculoResponse> listar(String placa, Long clienteId, Boolean ativo, Pageable pageable) {
        Specification<Veiculo> filtro = FiltroSpecs.todos(
                FiltroSpecs.contemIgnoreCase("placa", placa),
                FiltroSpecs.fkId("cliente", clienteId),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(VeiculoResponse::from);
    }

    @Transactional(readOnly = true)
    public Veiculo buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veiculo", id));
    }

    @Transactional
    public Veiculo criar(VeiculoRequest req) {
        String placa = normalizarPlaca(req.placa());
        if (repository.existsByPlaca(placa)) {
            throw new ConflitoException("Ja existe um veiculo cadastrado com esta placa.");
        }
        Veiculo veiculo = new Veiculo();
        aplicar(veiculo, req, placa);
        return repository.save(veiculo);
    }

    @Transactional
    public Veiculo atualizar(Long id, VeiculoRequest req) {
        Veiculo veiculo = buscar(id);
        String placa = normalizarPlaca(req.placa());
        if (repository.existsByPlacaAndIdNot(placa, id)) {
            throw new ConflitoException("Ja existe um veiculo cadastrado com esta placa.");
        }
        aplicar(veiculo, req, placa);
        return repository.save(veiculo);
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscar(id));
    }

    private void aplicar(Veiculo veiculo, VeiculoRequest req, String placa) {
        veiculo.setPlaca(placa);
        veiculo.setModelo(req.modelo());
        veiculo.setCor(req.cor());
        veiculo.setTipo(req.tipo() != null ? req.tipo() : TipoVeiculo.CARRO);
        veiculo.setCliente(req.clienteId() != null ? buscarCliente(req.clienteId()) : null);
        veiculo.setObservacao(req.observacao());
        if (req.ativo() != null) {
            veiculo.setAtivo(req.ativo());
        }
    }

    private String normalizarPlaca(String placa) {
        return placa.trim().toUpperCase();
    }

    private Cliente buscarCliente(Long clienteId) {
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", clienteId));
    }
}
