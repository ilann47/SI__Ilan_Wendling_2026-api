package com.kaneko.estacionamento.common.security;

import com.kaneko.estacionamento.common.exception.AutenticacaoException;
import com.kaneko.estacionamento.common.security.dto.LoginRequest;
import com.kaneko.estacionamento.common.security.dto.LoginResponse;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de autenticacao ({@code /api/auth}). Valida as credenciais com BCrypt
 * e emite um token JWT.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        Usuario usuario = usuarioRepository.findByLoginIgnoreCase(req.login().trim())
                .filter(Usuario::getAtivo)
                .orElseThrow(() -> new AutenticacaoException("Login ou senha inválidos."));
        if (!passwordEncoder.matches(req.senha(), usuario.getSenha())) {
            throw new AutenticacaoException("Login ou senha inválidos.");
        }
        String token = jwtService.gerarToken(usuario);
        return new LoginResponse(token, "Bearer", usuario.getLogin(), usuario.getPerfil());
    }
}
