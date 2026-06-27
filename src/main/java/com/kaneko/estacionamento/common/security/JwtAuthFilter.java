package com.kaneko.estacionamento.common.security;

import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro que extrai o token JWT do cabecalho {@code Authorization: Bearer ...} e,
 * quando valido, autentica a requisicao com o perfil como autoridade. Tokens
 * ausentes ou invalidos apenas seguem sem autenticacao (o endpoint protegido
 * respondera 401).
 *
 * <p>Instanciado manualmente no {@code SecurityConfig} (nao e um bean) para nao
 * ser auto-registrado na cadeia de filtros do servlet.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIXO)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtService.Claims claims = jwtService.validar(header.substring(PREFIXO.length()));
                // RN001: so autentica usuario existente e ATIVO — um token de usuario
                // inativado ou removido deixa de valer, mesmo dentro da validade.
                boolean usuarioAtivo = usuarioRepository.findByLoginIgnoreCase(claims.login())
                        .filter(Usuario::getAtivo)
                        .isPresent();
                if (usuarioAtivo) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            claims.login(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + claims.perfil())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (RuntimeException ignored) {
                // Token invalido: segue sem autenticacao.
            }
        }
        filterChain.doFilter(request, response);
    }
}
