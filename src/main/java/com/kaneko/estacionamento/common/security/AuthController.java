package com.kaneko.estacionamento.common.security;

import com.kaneko.estacionamento.common.exception.AutenticacaoException;
import com.kaneko.estacionamento.common.security.dto.LoginRequest;
import com.kaneko.estacionamento.common.security.dto.LoginResponse;
import com.kaneko.estacionamento.usuario.CadastroContaService;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioRepository;
import com.kaneko.estacionamento.usuario.dto.CadastroContaRequest;
import com.kaneko.estacionamento.usuario.dto.CadastroContaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@Tag(name = "Autenticacao", description = "Login e cadastro da identidade global")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CadastroContaService cadastroContaService;

    public AuthController(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          CadastroContaService cadastroContaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.cadastroContaService = cadastroContaService;
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

    @PostMapping("/register")
    @Operation(summary = "Criar conta global",
            description = "Cria uma identidade sem privilegios administrativos e emite um JWT global sem tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conta criada e autenticada"),
            @ApiResponse(responseCode = "400", description = "Payload invalido ou com campos nao permitidos"),
            @ApiResponse(responseCode = "409", description = "Login ou e-mail ja cadastrado")
    })
    public ResponseEntity<CadastroContaResponse> cadastrar(
            @Valid @RequestBody CadastroContaRequest request) {
        Usuario usuario = cadastroContaService.cadastrar(request);
        String token = jwtService.gerarToken(usuario);
        CadastroContaResponse response = new CadastroContaResponse(
                usuario.getId(), usuario.getNome(), usuario.getLogin(), usuario.getEmail(),
                token, "Bearer", usuario.getPerfil());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
