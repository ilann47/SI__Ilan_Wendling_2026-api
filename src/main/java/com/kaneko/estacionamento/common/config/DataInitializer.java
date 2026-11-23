package com.kaneko.estacionamento.common.config;

import com.kaneko.estacionamento.usuario.PerfilUsuario;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

/**
 * Cria um usuario administrador inicial quando o cadastro de usuarios esta vazio,
 * permitindo o primeiro acesso quando a seguranca esta ativa. Desligado nos
 * testes ({@code app.bootstrap-admin.enabled=false}).
 */
@Component
@Order(0)
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminLogin;
    private final String adminSenha;

    public DataInitializer(UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.bootstrap-admin.login:admin}") String adminLogin,
                           @Value("${app.bootstrap-admin.senha:admin123}") String adminSenha) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminLogin = adminLogin;
        this.adminSenha = adminSenha;
    }

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            return;
        }
        Usuario admin = new Usuario();
        admin.setNome("Administrador");
        admin.setLogin(adminLogin);
        admin.setSenha(passwordEncoder.encode(adminSenha));
        admin.setPerfil(PerfilUsuario.ADMIN);
        usuarioRepository.save(admin);
        log.warn("Usuario administrador inicial criado (login='{}'). TROQUE A SENHA apos o primeiro acesso.",
                adminLogin);
    }
}
