package br.com.jae.consultacnpj.infrastructure.adapter.in.web;

import br.com.jae.consultacnpj.application.port.in.ConsultarCnpjUseCase;
import br.com.jae.consultacnpj.domain.Cnpj;
import br.com.jae.consultacnpj.domain.EmpresaCnpj;
import br.com.jae.consultacnpj.domain.ResultadoConsulta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CnpjController {

    private static final Logger log = LoggerFactory.getLogger(CnpjController.class);

    private final ConsultarCnpjUseCase consultarCnpj;

    public CnpjController(ConsultarCnpjUseCase consultarCnpj) {
        this.consultarCnpj = consultarCnpj;
    }

    // A barra da máscara não é suportada no caminho, pois separa segmentos da URL.
    // Pontos e hífen funcionam; o Cnpj remove a formatação antes de validar.
    @GetMapping("/v1/cnpj/{cnpj}")
    public ResponseEntity<?> consultar(@PathVariable("cnpj") String valor) {
        Cnpj cnpj;
        try {
            cnpj = new Cnpj(valor);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErroResponse("CNPJ_INVALIDO", "CNPJ inválido"));
        }

        return switch (consultarCnpj.consultar(cnpj)) {
            case ResultadoConsulta.Encontrada encontrada ->
                    ResponseEntity.ok(paraResposta(encontrada.empresa()));
            case ResultadoConsulta.NaoEncontrada naoEncontrada ->
                    ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ErroResponse("CNPJ_NAO_ENCONTRADO", "CNPJ não encontrado"));
            case ResultadoConsulta.Indisponivel indisponivel -> {
                // O motivo pode conter detalhes internos e serve apenas para diagnóstico.
                log.warn("Fonte de CNPJ indisponível: {}", indisponivel.motivo());
                yield ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new ErroResponse(
                                "FONTE_INDISPONIVEL", "Serviço de consulta indisponível"));
            }
        };
    }

    private static CnpjResponse paraResposta(EmpresaCnpj empresa) {
        return new CnpjResponse(
                empresa.cnpj().value(),
                empresa.razaoSocial(),
                empresa.nomeFantasia(),
                empresa.situacaoCadastral().name(),
                empresa.cnaeFiscal(),
                empresa.municipio(),
                empresa.uf());
    }
}
