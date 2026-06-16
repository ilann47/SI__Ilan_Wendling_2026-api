package com.kaneko.estacionamento.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
            JwtService jwtService) throws Exception {

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (segurancaHabilitada) {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/auth/**", "/swagger-ui/**", "/swagger-ui.html",
                                    "/v3/api-docs/**", "/actuator/**").permitAll()
                            .anyRequest().authenticated())
                    .addFilterBefore(new JwtAuthFilter(jwtService),
                            UsernamePasswordAuthenticationFilter.class)
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }
}
