package com.kaneko.estacionamento.common.security;

import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import com.kaneko.estacionamento.rbac.RbacService;
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
    private final ContextoAutenticadoService contextoService;
    private final RbacService rbacService;

    public JwtAuthFilter(
            JwtService jwtService,
            UsuarioRepository usuarioRepository,
            ContextoAutenticadoService contextoService, RbacService rbacService) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.contextoService = contextoService;
        this.rbacService = rbacService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIXO)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtService.Claims claims = jwtService.validar(header.substring(PREFIXO.length()));
                Usuario usuario = buscarUsuarioAtivo(claims);
                ContextoOrganizacao contexto = validarContextoQuandoPresente(claims, usuario);
                UsuarioAutenticado principal = new UsuarioAutenticado(
                        usuario.getId(), usuario.getLogin(), usuario.getNome(), usuario.getEmail(),
                        usuario.getPerfil(),
                        contexto != null ? contexto.organizacaoId() : null,
                        contexto != null ? contexto.membershipId() : null,
                        contexto != null ? contexto.membershipVersion() : null);
                var authorities = new ArrayList<SimpleGrantedAuthority>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name()));
                if (contexto != null) rbacService.permissoes(contexto.membershipId(), contexto.organizacaoId())
                        .forEach(p -> authorities.add(new SimpleGrantedAuthority("PERMISSION_" + p)));
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) {
                // Token invalido: segue sem autenticacao.
            }
        }
        filterChain.doFilter(request, response);
    }

    private Usuario buscarUsuarioAtivo(JwtService.Claims claims) {
        if (claims.usuarioId() != null) {
            return usuarioRepository.findByIdAndAtivoTrue(claims.usuarioId())
                    .orElseThrow(() -> new JwtService.TokenInvalidoException("Usuario inativo ou inexistente."));
        }
        return usuarioRepository.findByLoginIgnoreCase(claims.login())
                .filter(Usuario::getAtivo)
                .orElseThrow(() -> new JwtService.TokenInvalidoException("Usuario inativo ou inexistente."));
    }

    private ContextoOrganizacao validarContextoQuandoPresente(
            JwtService.Claims claims, Usuario usuario) {
        if (!claims.possuiAlgumClaimDeContexto()) {
            return null;
        }
        if (!claims.possuiContextoCompleto()) {
            throw new TokenContextoInvalidoException();
        }
        return contextoService.validarContextoToken(
                claims.membershipId(), usuario.getId(), claims.organizacaoId(),
                claims.membershipVersion());
    }
}
