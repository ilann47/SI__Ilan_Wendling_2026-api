package com.kaneko.estacionamento.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Emissao e validacao de tokens JWT (HS256) sem dependencias externas: assina
 * {@code header.payload} com HMAC-SHA256 e valida assinatura + expiracao. Mantem
 * o projeto autocontido (apenas a JCA do proprio JDK).
 */
@Service
public class JwtService {

    private static final String ALGORITMO = "HmacSHA256";
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final byte[] secret;
    private final long expiracaoSegundos;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes:480}") long expiracaoMinutos) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expiracaoSegundos = expiracaoMinutos * 60L;
    }

    /** Gera um token com o login no subject e o perfil como claim. */
    public String gerarToken(String login, String perfil) {
        long agora = System.currentTimeMillis() / 1000L;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", login);
        payload.put("perfil", perfil);
        payload.put("iat", agora);
        payload.put("exp", agora + expiracaoSegundos);

        String header = encoder.encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String corpo;
        try {
            corpo = encoder.encodeToString(mapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar o token.", e);
        }
        String conteudo = header + "." + corpo;
        return conteudo + "." + encoder.encodeToString(hmac(conteudo));
    }

    /** Valida assinatura e expiracao e devolve as claims; lanca em caso de erro. */
    public Claims validar(String token) {
        String[] partes = token.split("\\.");
        if (partes.length != 3) {
            throw new TokenInvalidoException("Formato de token inválido.");
        }
        String conteudo = partes[0] + "." + partes[1];
        String assinaturaEsperada = encoder.encodeToString(hmac(conteudo));
        if (!constantTimeEquals(assinaturaEsperada, partes[2])) {
            throw new TokenInvalidoException("Assinatura inválida.");
        }
        try {
            JsonNode payload = mapper.readTree(decoder.decode(partes[1]));
            long exp = payload.path("exp").asLong();
            if (System.currentTimeMillis() / 1000L >= exp) {
                throw new TokenInvalidoException("Token expirado.");
            }
            return new Claims(payload.path("sub").asText(), payload.path("perfil").asText());
        } catch (TokenInvalidoException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenInvalidoException("Conteúdo do token inválido.");
        }
    }

    private byte[] hmac(String conteudo) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secret, ALGORITMO));
            return mac.doFinal(conteudo.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar o token.", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int resultado = 0;
        for (int i = 0; i < a.length(); i++) {
            resultado |= a.charAt(i) ^ b.charAt(i);
        }
        return resultado == 0;
    }

    /** Dados extraidos de um token valido. */
    public record Claims(String login, String perfil) {
    }

    /** Sinaliza um token ausente, malformado, adulterado ou expirado. */
    public static class TokenInvalidoException extends RuntimeException {
        public TokenInvalidoException(String mensagem) {
            super(mensagem);
        }
    }
}
