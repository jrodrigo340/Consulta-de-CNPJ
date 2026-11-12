package br.com.jae.consultacnpj.infrastructure.adapter.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErroResponse> tratar(RuntimeException e) {
        if (e instanceof ErrorResponse errorResponse) {
            // Exceções do próprio Spring já carregam um status HTTP; não são falhas internas
            // e a mensagem original não deve ser propagada para o cliente.
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .body(new ErroResponse("REQUISICAO_INVALIDA", "Requisição inválida"));
        }

        log.error("Erro interno inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErroResponse("ERRO_INTERNO", "Erro interno"));
    }
}
