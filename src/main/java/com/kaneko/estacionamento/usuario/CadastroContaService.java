package com.kaneko.estacionamento.usuario;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.usuario.dto.CadastroContaRequest;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cria identidade global neutra sem permitir elevacao de privilegio no payload. */
@Service
public class CadastroContaService {

    private static final String MENSAGEM_DUPLICIDADE = "Login ou e-mail já cadastrado.";

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public CadastroContaService(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Usuario cadastrar(CadastroContaRequest request) {
        String login = normalizarIdentificador(request.getLogin());
        String email = normalizarIdentificador(request.getEmail());
        if (repository.existsByLoginIgnoreCase(login) || repository.existsByEmailIgnoreCase(email)) {
            throw new ConflitoException(MENSAGEM_DUPLICIDADE);
        }

        Usuario usuario = new Usuario();
        usuario.setNome(request.getNome());
        usuario.setLogin(login);
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(request.getSenha()));
        usuario.setPerfil(PerfilUsuario.USUARIO);
        usuario.setAtivo(true);
        usuario.setFuncionario(null);

        try {
            return repository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflitoException(MENSAGEM_DUPLICIDADE);
        }
    }

    private String normalizarIdentificador(String valor) {
        return valor.trim().toLowerCase(Locale.ROOT);
    }
}
