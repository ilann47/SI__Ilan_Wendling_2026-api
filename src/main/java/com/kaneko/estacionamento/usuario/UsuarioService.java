package com.kaneko.estacionamento.usuario;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.rh.Funcionario;
import com.kaneko.estacionamento.rh.FuncionarioRepository;
import com.kaneko.estacionamento.usuario.dto.UsuarioRequest;
import com.kaneko.estacionamento.usuario.dto.UsuarioResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de usuarios: unicidade de login/email,
 * vinculo unico por funcionario e hashing BCrypt da senha.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final FuncionarioRepository funcionarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository repository,
                          FuncionarioRepository funcionarioRepository,
                          PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.funcionarioRepository = funcionarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<UsuarioResponse> listar(PerfilUsuario perfil, Boolean ativo, Pageable pageable) {
        Specification<Usuario> filtro = FiltroSpecs.todos(
                FiltroSpecs.igual("perfil", perfil),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(UsuarioResponse::from);
    }

    @Transactional(readOnly = true)
    public Usuario buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario", id));
    }

    @Transactional
    public Usuario criar(UsuarioRequest req) {
        Usuario usuario = new Usuario();
        String login = req.login().trim();
        if (repository.existsByLoginIgnoreCase(login)) {
            throw new ConflitoException("Já existe um usuário com este login.");
        }
        validarEmail(req.email(), null);
        validarFuncionario(req.funcionarioId(), null);
        aplicar(usuario, req, login);
        usuario.setSenha(passwordEncoder.encode(req.senha()));
        return repository.save(usuario);
    }

    @Transactional
    public Usuario atualizar(Long id, UsuarioRequest req) {
        Usuario usuario = buscar(id);
        String login = req.login().trim();
        if (repository.existsByLoginIgnoreCaseAndIdNot(login, id)) {
            throw new ConflitoException("Já existe um usuário com este login.");
        }
        validarEmail(req.email(), id);
        validarFuncionario(req.funcionarioId(), id);
        aplicar(usuario, req, login);
        usuario.setSenha(passwordEncoder.encode(req.senha()));
        return repository.save(usuario);
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).setAtivo(false);
    }

    private void aplicar(Usuario usuario, UsuarioRequest req, String login) {
        usuario.setNome(req.nome().trim());
        usuario.setLogin(login);
        usuario.setEmail(req.email() != null && !req.email().isBlank() ? req.email().trim() : null);
        usuario.setPerfil(req.perfil() != null ? req.perfil() : PerfilUsuario.OPERADOR);
        usuario.setFuncionario(req.funcionarioId() != null ? buscarFuncionario(req.funcionarioId()) : null);
    }

    private void validarEmail(String email, Long idAtual) {
        if (email == null || email.isBlank()) {
            return;
        }
        boolean existe = idAtual == null
                ? repository.existsByEmailIgnoreCase(email.trim())
                : repository.existsByEmailIgnoreCaseAndIdNot(email.trim(), idAtual);
        if (existe) {
            throw new ConflitoException("Já existe um usuário com este e-mail.");
        }
    }

    private void validarFuncionario(Long funcionarioId, Long idAtual) {
        if (funcionarioId == null) {
            return;
        }
        boolean existe = idAtual == null
                ? repository.existsByFuncionarioId(funcionarioId)
                : repository.existsByFuncionarioIdAndIdNot(funcionarioId, idAtual);
        if (existe) {
            throw new ConflitoException("Este funcionário já possui um usuário vinculado.");
        }
    }

    private Funcionario buscarFuncionario(Long id) {
        return funcionarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Funcionario", id));
    }
}
