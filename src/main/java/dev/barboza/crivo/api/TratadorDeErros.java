package dev.barboza.crivo.api;

import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import dev.barboza.crivo.dominio.NaoEncontradoException;
import dev.barboza.crivo.dominio.RegraVioladaException;
import dev.barboza.crivo.dominio.TransicaoInvalidaException;

/** Erros da API em application/problem+json (RFC 9457), em português. */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(NaoEncontradoException.class)
    ProblemDetail naoEncontrado(NaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Não encontrado", e.getMessage());
    }

    @ExceptionHandler(RegraVioladaException.class)
    ProblemDetail regra(RegraVioladaException e) {
        return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Regra do processo seletivo", e.getMessage());
    }

    @ExceptionHandler(TransicaoInvalidaException.class)
    ProblemDetail transicao(TransicaoInvalidaException e) {
        return problema(HttpStatus.CONFLICT, "Mudança de etapa não permitida", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacao(MethodArgumentNotValidException e) {
        return problema(HttpStatus.BAD_REQUEST, "Dados inválidos", e.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getDefaultMessage()).distinct().collect(Collectors.joining(" ")));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail formato(Exception e) {
        return problema(HttpStatus.BAD_REQUEST, "Dados inválidos",
                "Não foi possível ler a requisição. Confira o JSON, os números (use ponto: 3500.00) e o nome da etapa.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concorrencia(OptimisticLockingFailureException e) {
        return problema(HttpStatus.CONFLICT, "Alterado ao mesmo tempo",
                "Outra pessoa alterou este candidato agora mesmo. Atualize a página e tente de novo.");
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
