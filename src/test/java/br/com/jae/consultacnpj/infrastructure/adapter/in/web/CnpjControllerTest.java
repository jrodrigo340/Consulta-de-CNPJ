package br.com.jae.consultacnpj.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.jae.consultacnpj.application.port.in.ConsultarCnpjUseCase;
import br.com.jae.consultacnpj.domain.Cnpj;
import br.com.jae.consultacnpj.domain.EmpresaCnpj;
import br.com.jae.consultacnpj.domain.ResultadoConsulta;
import br.com.jae.consultacnpj.domain.SituacaoCadastral;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class CnpjControllerTest {

    private static final Cnpj CNPJ = new Cnpj("12ABC34501DE35");
    private static final EmpresaCnpj EMPRESA = new EmpresaCnpj(
            CNPJ,
            "Empresa Exemplo Ltda",
            "Exemplo",
            SituacaoCadastral.ATIVA,
            "0111301",
            "São Paulo",
            "SP");

    @Test
    void encontradaRetorna200ComTodosOsCampos() throws Exception {
        MockMvc mockMvc = mockMvc(new UseCaseFalso(new ResultadoConsulta.Encontrada(EMPRESA)));

        mockMvc.perform(get("/v1/cnpj/12ABC34501DE35"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.cnpj").value("12ABC34501DE35"))
                .andExpect(jsonPath("$.razaoSocial").value("Empresa Exemplo Ltda"))
                .andExpect(jsonPath("$.nomeFantasia").value("Exemplo"))
                .andExpect(jsonPath("$.situacaoCadastral").value("ATIVA"))
                .andExpect(jsonPath("$.cnaeFiscal").value("0111301"))
                .andExpect(jsonPath("$.municipio").value("São Paulo"))
                .andExpect(jsonPath("$.uf").value("SP"));
    }

    @Test
    void aceitaMinusculasEChamaUseCaseComCnpjNormalizado() throws Exception {
        UseCaseFalso useCase = new UseCaseFalso(new ResultadoConsulta.Encontrada(EMPRESA));

        mockMvc(useCase).perform(get("/v1/cnpj/12abc34501de35"))
                .andExpect(status().isOk());

        assertEquals(new Cnpj("12ABC34501DE35"), useCase.ultimoCnpj);
    }

    @Test
    void aceitaMascaraComPontosEHifenEChamaUseCaseNormalizado() throws Exception {
        UseCaseFalso useCase = new UseCaseFalso(new ResultadoConsulta.Encontrada(EMPRESA));

        mockMvc(useCase).perform(get("/v1/cnpj/12.ABC.34501DE-35"))
                .andExpect(status().isOk());

        assertEquals(new Cnpj("12ABC34501DE35"), useCase.ultimoCnpj);
    }

    static Stream<String> cnpjsInvalidos() {
        return Stream.of("123", "12ABC34501DE36", "ABCDEFGHIJKLMN", "9".repeat(5000));
    }

    @ParameterizedTest
    @MethodSource("cnpjsInvalidos")
    void cnpjInvalidoRetorna400SemChamarUseCaseENemEcoarValor(String valor) throws Exception {
        UseCaseFalso useCase = new UseCaseFalso(new ResultadoConsulta.Encontrada(EMPRESA));

        String corpo = mockMvc(useCase).perform(get("/v1/cnpj/{cnpj}", valor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CNPJ_INVALIDO"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(0, useCase.chamadas);
        assertFalse(corpo.contains(valor));
    }

    @Test
    void naoEncontradaRetorna404() throws Exception {
        MockMvc mockMvc = mockMvc(new UseCaseFalso(new ResultadoConsulta.NaoEncontrada()));

        mockMvc.perform(get("/v1/cnpj/12ABC34501DE35"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CNPJ_NAO_ENCONTRADO"));
    }

    @Test
    void indisponivelRetorna503SemExporMotivo() throws Exception {
        MockMvc mockMvc = mockMvc(
                new UseCaseFalso(new ResultadoConsulta.Indisponivel("segredo-interno")));

        String corpo = mockMvc.perform(get("/v1/cnpj/12ABC34501DE35"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("FONTE_INDISPONIVEL"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(corpo.contains("segredo-interno"));
    }

    @Test
    void runtimeExceptionRetorna500SemExporDetalhe() throws Exception {
        MockMvc mockMvc = mockMvc(new UseCaseFalso(new RuntimeException("detalhe-interno")));

        String corpo = mockMvc.perform(get("/v1/cnpj/12ABC34501DE35"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(corpo.contains("detalhe-interno"));
    }

    @Test
    void responseStatusExceptionUsaStatusOriginalSemLogDeErro() throws Exception {
        MockMvc mockMvc = mockMvc(new UseCaseFalso(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "detalhe-interno")));

        String corpo = mockMvc.perform(get("/v1/cnpj/12ABC34501DE35"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(corpo.contains("detalhe-interno"));
    }

    @Test
    void postNoMesmoCaminhoRetorna405() throws Exception {
        MockMvc mockMvc = mockMvc(new UseCaseFalso(new ResultadoConsulta.Encontrada(EMPRESA)));

        mockMvc.perform(post("/v1/cnpj/12ABC34501DE35"))
                .andExpect(status().isMethodNotAllowed());
    }

    private static MockMvc mockMvc(ConsultarCnpjUseCase useCase) {
        return MockMvcBuilders.standaloneSetup(new CnpjController(useCase))
                .setControllerAdvice(new TratadorDeErros())
                .build();
    }

    private static final class UseCaseFalso implements ConsultarCnpjUseCase {

        private final ResultadoConsulta resultado;
        private final RuntimeException erro;
        private int chamadas;
        private Cnpj ultimoCnpj;

        private UseCaseFalso(ResultadoConsulta resultado) {
            this.resultado = resultado;
            this.erro = null;
        }

        private UseCaseFalso(RuntimeException erro) {
            this.resultado = null;
            this.erro = erro;
        }

        @Override
        public ResultadoConsulta consultar(Cnpj cnpj) {
            chamadas++;
            ultimoCnpj = cnpj;
            if (erro != null) {
                throw erro;
            }
            return resultado;
        }
    }
}
