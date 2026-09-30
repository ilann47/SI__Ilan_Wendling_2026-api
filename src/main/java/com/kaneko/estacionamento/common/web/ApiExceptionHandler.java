package com.kaneko.estacionamento.common.web;

import com.kaneko.estacionamento.common.exception.AutenticacaoException;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Tratamento centralizado de erros da API, padronizando as respostas no formato
 * {@link ProblemDetail} (RFC 7807). Todo problema carrega o {@code traceId} da
 * requisicao para correlacao com os logs.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail handleNaoEncontrado(RecursoNaoEncontradoException ex) {
        return problem(HttpStatus.NOT_FOUND, "Recurso nao encontrado", ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleRotaNaoEncontrada(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Rota nao encontrada",
                "O endereco solicitado nao existe nesta API.");
    }

    @ExceptionHandler(AutenticacaoException.class)
    public ProblemDetail handleAutenticacao(AutenticacaoException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Nao autenticado", ex.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail handleConflito(ConflitoException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, "Conflito", ex.getMessage());
        if (!ex.erros().isEmpty()) {
            pd.setProperty("erros", ex.erros());
        }
        return pd;
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ProblemDetail handleAcessoNegado(AuthorizationDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Acesso negado",
                "O usuario nao possui permissao efetiva para esta operacao e escopo.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegridade(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Conflito de integridade",
                "A operacao viola uma restricao de integridade (por exemplo, registro vinculado a outros ou valor duplicado).");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConcorrenciaOtimista(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Conflito de concorrencia",
                "O registro foi alterado concorrentemente. Recarregue os dados e tente novamente; a operacao atual foi revertida.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidacao(MethodArgumentNotValidException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Requisicao invalida",
                "Ha campos invalidos na requisicao.");
        Map<String, String> erros = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            erros.put(fe.getField(), fe.getDefaultMessage());
        }
        pd.setProperty("erros", erros);
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleCorpoIlegivel(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Requisicao invalida",
                "O corpo da requisicao esta ausente ou malformado.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTipoInvalido(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Requisicao invalida",
                "O parametro '" + ex.getName() + "' tem um valor invalido.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleParametroObrigatorio(MissingServletRequestParameterException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Requisicao invalida",
                "O parametro '" + ex.getParameterName() + "' e obrigatorio.");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleCabecalhoObrigatorio(MissingRequestHeaderException ex) {
        if ("If-Match".equalsIgnoreCase(ex.getHeaderName())) {
            return problem(HttpStatus.PRECONDITION_REQUIRED, "Pre-condicao obrigatoria",
                    "O cabecalho If-Match com a versao esperada e obrigatorio.");
        }
        return problem(HttpStatus.BAD_REQUEST, "Requisicao invalida",
                "O cabecalho '" + ex.getHeaderName() + "' e obrigatorio.");
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ProblemDetail handleValidacaoDeParametros(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Requisicao invalida",
                "Ha parametros invalidos na requisicao.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMetodoNaoPermitido(HttpRequestMethodNotSupportedException ex) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "Metodo nao permitido",
                "O metodo HTTP informado nao e aceito por este endereco.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMidiaNaoSuportada(HttpMediaTypeNotSupportedException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato nao suportado",
                "O formato do corpo da requisicao nao e aceito por este endereco.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenerico(Exception ex) {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        log.error("Erro nao tratado [traceId={}]", traceId, ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado. Utilize o traceId informado para suporte.");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        if (traceId != null) {
            pd.setProperty("traceId", traceId);
        }
        return pd;
    }
}
