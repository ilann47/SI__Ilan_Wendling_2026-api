package com.kaneko.estacionamento.common.security;

import com.kaneko.estacionamento.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuracao de seguranca. Em producao ({@code app.security.enabled=true}), os
 * endpoints {@code /api/**} exigem um token JWT valido — exceto {@code /api/auth},
 * o Swagger e o actuator. Em testes de cadastro a protecao e desligada
 * ({@code app.security.enabled=false}) para exercitar os controllers diretamente.
 *
 * <p>Autorizacao por perfil (segregacao de funcoes): operacoes administrativas
 * sao restritas a {@code ROLE_ADMIN} — gestao de usuarios, qualquer exclusao
 * ({@code DELETE}) e a manutencao de tarifas (preco). As demais rotas exigem
 * apenas autenticacao, liberando o OPERADOR para a operacao do dia a dia (pátio,
 * vendas, baixas, consultas). O perfil chega como autoridade {@code ROLE_<perfil>}
 * no token (ver {@link JwtAuthFilter}).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${app.security.enabled:true}") boolean segurancaHabilitada,
            JwtService jwtService,
            UsuarioRepository usuarioRepository) throws Exception {

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (segurancaHabilitada) {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/auth/**", "/swagger-ui/**", "/swagger-ui.html",
                                    "/v3/api-docs/**", "/actuator/**").permitAll()
                            // Gestao de usuarios e exclusivamente administrativa.
                            .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                            // Manutencao de tarifa (preco) e administrativa.
                            .requestMatchers(HttpMethod.POST, "/api/tarifas/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.PUT, "/api/tarifas/**").hasRole("ADMIN")
                            // Qualquer exclusao e administrativa.
                            .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                            .anyRequest().authenticated())
                    .addFilterBefore(new JwtAuthFilter(jwtService, usuarioRepository),
                            UsernamePasswordAuthenticationFilter.class)
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }
}
