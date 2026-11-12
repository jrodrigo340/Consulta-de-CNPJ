package br.com.jae.consultacnpj;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.jae.consultacnpj.application.port.in.ConsultarCnpjUseCase;
import br.com.jae.consultacnpj.application.port.out.FonteCnpj;
import br.com.jae.consultacnpj.domain.Cnpj;
import br.com.jae.consultacnpj.domain.EmpresaCnpj;
import br.com.jae.consultacnpj.domain.ResultadoConsulta;
import br.com.jae.consultacnpj.domain.SituacaoCadastral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ConsultaCnpjApplicationTest {

    private static final Cnpj CNPJ = new Cnpj("12ABC34501DE35");
    private static final EmpresaCnpj EMPRESA = new EmpresaCnpj(
            CNPJ,
            "Empresa Exemplo Ltda",
            "Exemplo",
            SituacaoCadastral.ATIVA,
            "0111301",
            "São Paulo",
            "SP");

    @TestBean(name = "brasilApiFonteCnpj", methodName = "fonteFalsa")
    FonteCnpj fonte;

    static FonteCnpj fonteFalsa() {
        return cnpj -> new ResultadoConsulta.Encontrada(EMPRESA);
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void contextoCarregaBeanConsultarCnpjUseCase() {
        assertNotNull(context.getBean(ConsultarCnpjUseCase.class));
    }

    @Test
    void consultaPontaAPontaComFonteFalsa() throws Exception {
        mockMvc.perform(get("/v1/cnpj/12ABC34501DE35"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cnpj").value("12ABC34501DE35"))
                .andExpect(jsonPath("$.razaoSocial").value("Empresa Exemplo Ltda"))
                .andExpect(jsonPath("$.nomeFantasia").value("Exemplo"))
                .andExpect(jsonPath("$.situacaoCadastral").value("ATIVA"))
                .andExpect(jsonPath("$.cnaeFiscal").value("0111301"))
                .andExpect(jsonPath("$.municipio").value("São Paulo"))
                .andExpect(jsonPath("$.uf").value("SP"));
    }

    @Test
    void contextoFalhaComBaseUrlInvalida() {
        RuntimeException excecao = assertThrows(RuntimeException.class, () ->
                new SpringApplicationBuilder(ConsultaCnpjApplication.class)
                        .web(WebApplicationType.NONE)
                        .run("--consulta-cnpj.brasilapi.base-url=//localhost:8080")
                        .close());

        Throwable causa = excecao;
        while (causa != null && !(causa instanceof IllegalArgumentException)) {
            causa = causa.getCause();
        }

        assertNotNull(causa, "Esperava IllegalArgumentException na cadeia de causas");
        assertTrue(causa.getMessage().contains("http") || causa.getMessage().contains("https"));
    }
}
