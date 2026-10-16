package com.kaneko.estacionamento.common.config;

import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Escape hatch de ambiente. Em sandboxes que bloqueiam o self-pipe de loopback do
 * seletor NIO ({@code java.nio.channels.Selector.open} -> WEPoll/EPoll), o conector
 * NIO padrao do Tomcat falha ao iniciar ("Unable to establish loopback connection").
 * O conector NIO2 usa I/O assincrono (IOCP no Windows) e nao abre esse seletor.
 *
 * <p>Ativo apenas com a flag de sistema {@code -Dtomcat.nio2=true}; sem ela, nada
 * muda (NIO padrao em testes e producao).
 */
@Component
public class Nio2WebServerCustomizer
        implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private static final String NIO2_PROTOCOL = "org.apache.coyote.http11.Http11Nio2Protocol";

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        if (Boolean.getBoolean("tomcat.nio2")) {
            factory.setProtocol(NIO2_PROTOCOL);
        }
    }
}
