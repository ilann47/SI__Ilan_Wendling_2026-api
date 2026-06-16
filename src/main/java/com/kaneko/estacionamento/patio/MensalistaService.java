package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.patio.dto.MensalistaRequest;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do contrato de Mensalista.
 *
 * <p>Valida as referencias a cliente e veiculo, a coerencia do periodo
 * ({@code data_fim >= data_inicio}) e a regra de no maximo um contrato ATIVO
 * por veiculo (espelhando {@code uq_mensalista_veiculo_ativo}).
 */
@Service
public class MensalistaService {

    private final MensalistaRepository repository;
    private final ClienteRepository clienteRepository;
    private final VeiculoRepository veiculoRepository;

    public MensalistaService(MensalistaRepository repository,
                             ClienteRepository clienteRepository,
                             VeiculoRepository veiculoRepository) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.veiculoRepository = veiculoRepository;
    }

    @Transactional(readOnly = true)
    public Page<Mensalista> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Mensalista buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mensalista", id));
    }

    @Transactional
    public Mensalista criar(MensalistaRequest req) {
        Cliente cliente = buscarCliente(req.clienteId());
        Veiculo veiculo = buscarVeiculo(req.veiculoId());
        StatusMensalista status = req.status() != null ? req.status() : StatusMensalista.ATIVO;
        LocalDate inicio = req.dataInicio() != null ? req.dataInicio() : LocalDate.now();
        validarPeriodo(inicio, req.dataFim());
        if (status == StatusMensalista.ATIVO
                && repository.existsByVeiculoIdAndStatus(veiculo.getId(), StatusMensalista.ATIVO)) {
            throw new ConflitoException("Ja existe um contrato de mensalista ATIVO para este veiculo.");
        }
        Mensalista mensalista = new Mensalista();
        mensalista.setCliente(cliente);
        mensalista.setVeiculo(veiculo);
        aplicar(mensalista, req, status, inicio);
        return repository.save(mensalista);
    }

    @Transactional
    public Mensalista atualizar(Long id, MensalistaRequest req) {
        Mensalista mensalista = buscar(id);
        Cliente cliente = buscarCliente(req.clienteId());
        Veiculo veiculo = buscarVeiculo(req.veiculoId());
        StatusMensalista status = req.status() != null ? req.status() : StatusMensalista.ATIVO;
        LocalDate inicio = req.dataInicio() != null ? req.dataInicio() : mensalista.getDataInicio();
        validarPeriodo(inicio, req.dataFim());
        if (status == StatusMensalista.ATIVO
                && repository.existsByVeiculoIdAndStatusAndIdNot(veiculo.getId(), StatusMensalista.ATIVO, id)) {
            throw new ConflitoException("Ja existe um contrato de mensalista ATIVO para este veiculo.");
        }
        mensalista.setCliente(cliente);
        mensalista.setVeiculo(veiculo);
        aplicar(mensalista, req, status, inicio);
        return repository.save(mensalista);
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscar(id));
    }

    private void aplicar(Mensalista mensalista, MensalistaRequest req, StatusMensalista status, LocalDate inicio) {
        mensalista.setValorMensal(req.valorMensal());
        mensalista.setDataInicio(inicio);
        mensalista.setDataFim(req.dataFim());
        mensalista.setStatus(status);
    }

    private void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (fim != null && fim.isBefore(inicio)) {
            throw new ConflitoException("A data de término não pode ser anterior à data de início.");
        }
    }

    private Cliente buscarCliente(Long clienteId) {
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", clienteId));
    }

    private Veiculo buscarVeiculo(Long veiculoId) {
        return veiculoRepository.findById(veiculoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veiculo", veiculoId));
    }
}
