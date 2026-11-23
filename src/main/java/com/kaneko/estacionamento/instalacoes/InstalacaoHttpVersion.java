package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.organizacao.venue.VenueController;
import java.util.Objects;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        VenueController.class, InstalacoesController.class, SetorController.class, VagaController.class})
public final class InstalacaoHttpVersion {
    public static long ler(String header) {
        if (!header.matches("\"[0-9]+\"")) {
            throw new IllegalArgumentException("If-Match deve conter a versão esperada entre aspas.");
        }
        return Long.parseLong(header.substring(1, header.length() - 1));
    }

    public static void exigir(Long atual, long esperada) {
        if (!Objects.equals(atual, esperada)) throw new VersaoDesatualizada();
    }

    @ExceptionHandler(VersaoDesatualizada.class)
    public ProblemDetail versaoDesatualizada() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED,
                "O cadastro foi alterado. Reabra a edição para carregar os dados atuais.");
    }

    static final class VersaoDesatualizada extends RuntimeException {}
}
