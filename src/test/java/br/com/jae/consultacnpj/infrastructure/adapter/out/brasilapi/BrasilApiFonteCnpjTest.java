package br.com.jae.consultacnpj.infrastructure.adapter.out.brasilapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.jae.consultacnpj.domain.Cnpj;
import br.com.jae.consultacnpj.domain.EmpresaCnpj;
import br.com.jae.consultacnpj.domain.ResultadoConsulta;
import br.com.jae.consultacnpj.domain.SituacaoCadastral;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BrasilApiFonteCnpjTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(200);
    private static final Cnpj CNPJ = new Cnpj("12ABC34501DE35");

    private static final String JSON_COMPLETO = """
            {
              "cnpj": "12ABC34501DE35",
              "razao_social": "Empresa Exemplo Ltda",
              "nome_fantasia": "Exemplo",
              "situacao_cadastral": 2,
              "cnae_fiscal": 111301,
              "municipio": "São Paulo",
              "uf": "SP"
            }
            """;

    @Test
    void mapeiaRespostaCompleta() throws Exception {
        comServidor(exchange -> responder(exchange, 200, JSON_COMPLETO), fonte -> {
            ResultadoConsulta resultado = fonte.buscar(CNPJ);

            EmpresaCnpj empresa = assertInstanceOf(
                    ResultadoConsulta.Encontrada.class, resultado).empresa();
            assertEquals(new Cnpj("12ABC34501DE35"), empresa.cnpj());
            assertEquals("Empresa Exemplo Ltda", empresa.razaoSocial());
            assertEquals("Exemplo", empresa.nomeFantasia());
            assertEquals(SituacaoCadastral.ATIVA, empresa.situacaoCadastral());
            assertEquals("0111301", empresa.cnaeFiscal());
            assertEquals("São Paulo", empresa.municipio());
            assertEquals("SP", empresa.uf());
        });
    }

    @Test
    void mapeiaCnaeFiscalComZerosAEsquerda() throws Exception {
        String json = jsonCom("111301", null);

        comServidor(exchange -> responder(exchange, 200, json), fonte -> {
            EmpresaCnpj empresa = assertInstanceOf(
                    ResultadoConsulta.Encontrada.class, fonte.buscar(CNPJ)).empresa();

            assertEquals("0111301", empresa.cnaeFiscal());
        });
    }

    @Test
    void nomeFantasiaVazioViraNull() throws Exception {
        String json = """
                {
                  "cnpj": "12ABC34501DE35",
                  "razao_social": "Empresa Exemplo Ltda",
                  "nome_fantasia": ""
                }
                """;

        comServidor(exchange -> responder(exchange, 200, json), fonte -> {
            EmpresaCnpj empresa = assertInstanceOf(
                    ResultadoConsulta.Encontrada.class, fonte.buscar(CNPJ)).empresa();

            assertNull(empresa.nomeFantasia());
        });
    }

    @ParameterizedTest
    @CsvSource({
            "1, NULA",
            "2, ATIVA",
            "3, SUSPENSA",
            "4, INAPTA",
            "8, BAIXADA",
            "99, DESCONHECIDA"
    })
    void mapeiaSituacaoCadastral(int codigo, SituacaoCadastral esperada) throws Exception {
        String json = jsonCom(null, codigo);

        comServidor(exchange -> responder(exchange, 200, json), fonte -> {
            EmpresaCnpj empresa = assertInstanceOf(
                    ResultadoConsulta.Encontrada.class, fonte.buscar(CNPJ)).empresa();

            assertEquals(esperada, empresa.situacaoCadastral());
        });
    }

    @Test
    void ignoraCamposDesconhecidos() throws Exception {
        String json = """
                {
                  "cnpj": "12ABC34501DE35",
                  "razao_social": "Empresa Exemplo Ltda",
                  "situacao_cadastral": 2,
                  "campo_desconhecido": { "qualquer": "coisa" }
                }
                """;

        comServidor(exchange -> responder(exchange, 200, json), fonte -> {
            ResultadoConsulta resultado = fonte.buscar(CNPJ);

            assertInstanceOf(ResultadoConsulta.Encontrada.class, resultado);
        });
    }

    @Test
    void status404ViraNaoEncontrada() throws Exception {
        comServidor(exchange -> responder(exchange, 404, ""), fonte -> {
            ResultadoConsulta resultado = fonte.buscar(CNPJ);

            assertInstanceOf(ResultadoConsulta.NaoEncontrada.class, resultado);
        });
    }

    @ParameterizedTest
    @CsvSource({
            "429, limite de requisições da BrasilAPI",
            "400, HTTP 400",
            "500, HTTP 500"
    })
    void statusDeErroViraIndisponivel(int status, String motivoEsperado) throws Exception {
        comServidor(exchange -> responder(exchange, status, ""), fonte -> {
            ResultadoConsulta.Indisponivel indisponivel =
                    assertInstanceOf(ResultadoConsulta.Indisponivel.class, fonte.buscar(CNPJ));

            assertEquals(motivoEsperado, indisponivel.motivo());
        });
    }

    @Test
    void respostaMaisLentaQueTimeoutViraIndisponivel() throws Exception {
        comServidor(exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            responder(exchange, 200, JSON_COMPLETO);
        }, fonte -> {
            ResultadoConsulta.Indisponivel indisponivel =
                    assertInstanceOf(ResultadoConsulta.Indisponivel.class, fonte.buscar(CNPJ));

            assertEquals("HttpTimeoutException", indisponivel.motivo());
        });
    }

    @Test
    void servidorParadoViraIndisponivel() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        URI baseUrl = baseUrl(server);
        server.stop(0);

        BrasilApiFonteCnpj fonte = new BrasilApiFonteCnpj(
                BrasilApiFonteCnpj.criarHttpClient(CONNECT_TIMEOUT), baseUrl, REQUEST_TIMEOUT);

        ResultadoConsulta.Indisponivel indisponivel =
                assertInstanceOf(ResultadoConsulta.Indisponivel.class, fonte.buscar(CNPJ));

        assertEquals("ConnectException", indisponivel.motivo());
    }

    @Test
    void jsonMalformadoViraIndisponivel() throws Exception {
        comServidor(exchange -> responder(exchange, 200, "{ isto não é json"), fonte -> {
            ResultadoConsulta.Indisponivel indisponivel =
                    assertInstanceOf(ResultadoConsulta.Indisponivel.class, fonte.buscar(CNPJ));

            assertEquals("resposta inválida", indisponivel.motivo());
        });
    }

    @Test
    void raizQueNaoEObjetoViraIndisponivel() throws Exception {
        comServidor(exchange -> responder(exchange, 200, "[]"), fonte -> {
            ResultadoConsulta.Indisponivel indisponivel =
                    assertInstanceOf(ResultadoConsulta.Indisponivel.class, fonte.buscar(CNPJ));

            assertEquals("resposta inválida", indisponivel.motivo());
        });
    }

    @Test
    void razaoSocialAusenteViraIndisponivel() throws Exception {
        String json = """
                {
                  "cnpj": "12ABC34501DE35",
                  "situacao_cadastral": 2
                }
                """;

        comServidor(exchange -> responder(exchange, 200, json), fonte -> {
            ResultadoConsulta.Indisponivel indisponivel =
                    assertInstanceOf(ResultadoConsulta.Indisponivel.class, fonte.buscar(CNPJ));

            assertEquals("resposta inválida", indisponivel.motivo());
        });
    }

    @Test
    void cnpjInvalidoNaRespostaViraIndisponivel() throws Exception {
        String json = """
                {
                  "cnpj": "123",
                  "razao_social": "Empresa Exemplo Ltda",
                  "situacao_cadastral": 2
                }
                """;

        comServidor(exchange -> responder(exchange, 200, json), fonte -> {
            ResultadoConsulta.Indisponivel indisponivel =
                    assertInstanceOf(ResultadoConsulta.Indisponivel.class, fonte.buscar(CNPJ));

            assertEquals("resposta com CNPJ inválido", indisponivel.motivo());
        });
    }

    @Test
    void respostaSemCampoCnpjViraIndisponivel() throws Exception {
        String json = """
                {
                  "razao_social": "Empresa Exemplo Ltda",
                  "situacao_cadastral": 2
                }
                """;

        comServidor(exchange -> responder(exchange, 200, json), fonte -> {
            ResultadoConsulta.Indisponivel indisponivel =
                    assertInstanceOf(ResultadoConsulta.Indisponivel.class, fonte.buscar(CNPJ));

            assertEquals("resposta com CNPJ inválido", indisponivel.motivo());
        });
    }

    @Test
    void corpoVazioViraIndisponivel() throws Exception {
        comServidor(exchange -> responder(exchange, 200, ""), fonte -> {
            ResultadoConsulta.Indisponivel indisponivel =
                    assertInstanceOf(ResultadoConsulta.Indisponivel.class, fonte.buscar(CNPJ));

            assertEquals("resposta inválida", indisponivel.motivo());
        });
    }

    @Test
    void usaCaminhoComCnpjAlfanumerico() throws Exception {
        AtomicReference<String> caminhoRecebido = new AtomicReference<>();

        comServidor(exchange -> {
            caminhoRecebido.set(exchange.getRequestURI().getPath());
            responder(exchange, 404, "");
        }, fonte -> {
            ResultadoConsulta resultado = fonte.buscar(new Cnpj("12ABC34501DE35"));

            assertInstanceOf(ResultadoConsulta.NaoEncontrada.class, resultado);
            assertEquals("/api/cnpj/v1/12ABC34501DE35", caminhoRecebido.get());
        });
    }

    @Test
    void cnaeNaoNumericoViraNull() throws Exception {
        assertCnaeMapeado("\"abc\"", null);
    }

    @Test
    void cnaeNegativoViraNull() throws Exception {
        assertCnaeMapeado("-5", null);
    }

    @Test
    void cnaeAcimaDoLimiteViraNull() throws Exception {
        assertCnaeMapeado("12345678", null);
    }

    @Test
    void cnaeMaiorQueIntViraNull() throws Exception {
        assertCnaeMapeado("18446744073709551621", null);
    }

    @Test
    void cnaeTextoComSeteDigitosMantemValor() throws Exception {
        assertCnaeMapeado("\"6201500\"", "6201500");
    }

    @Test
    void cnaeTextoComSeisDigitosRecebeZerosAEsquerda() throws Exception {
        assertCnaeMapeado("\"111301\"", "0111301");
    }

    @Test
    void rejeitaBaseUrlSemEsquema() {
        assertThrows(IllegalArgumentException.class, () -> new BrasilApiFonteCnpj(
                BrasilApiFonteCnpj.criarHttpClient(CONNECT_TIMEOUT),
                URI.create("//localhost:8080"),
                REQUEST_TIMEOUT));
    }

    @Test
    void rejeitaBaseUrlComEsquemaFtp() {
        assertThrows(IllegalArgumentException.class, () -> new BrasilApiFonteCnpj(
                BrasilApiFonteCnpj.criarHttpClient(CONNECT_TIMEOUT),
                URI.create("ftp://localhost:8080"),
                REQUEST_TIMEOUT));
    }

    @Test
    void razaoSocialNumericaViraIndisponivel() throws Exception {
        String json = """
                {
                  "cnpj": "12ABC34501DE35",
                  "razao_social": 12345,
                  "situacao_cadastral": 2
                }
                """;

        comServidor(exchange -> responder(exchange, 200, json), fonte -> {
            ResultadoConsulta.Indisponivel indisponivel =
                    assertInstanceOf(ResultadoConsulta.Indisponivel.class, fonte.buscar(CNPJ));

            assertEquals("resposta inválida", indisponivel.motivo());
        });
    }

    @Test
    void municipioNumericoViraNull() throws Exception {
        String json = """
                {
                  "cnpj": "12ABC34501DE35",
                  "razao_social": "Empresa Exemplo Ltda",
                  "situacao_cadastral": 2,
                  "municipio": 3550308
                }
                """;

        comServidor(exchange -> responder(exchange, 200, json), fonte -> {
            EmpresaCnpj empresa = assertInstanceOf(
                    ResultadoConsulta.Encontrada.class, fonte.buscar(CNPJ)).empresa();

            assertNull(empresa.municipio());
        });
    }

    @Test
    void ufBooleanoViraNull() throws Exception {
        String json = """
                {
                  "cnpj": "12ABC34501DE35",
                  "razao_social": "Empresa Exemplo Ltda",
                  "situacao_cadastral": 2,
                  "uf": true
                }
                """;

        comServidor(exchange -> responder(exchange, 200, json), fonte -> {
            EmpresaCnpj empresa = assertInstanceOf(
                    ResultadoConsulta.Encontrada.class, fonte.buscar(CNPJ)).empresa();

            assertNull(empresa.uf());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "//"})
    void baseUrlComBarrasFinaisNaoDuplicaCaminho(String barrasFinais) throws Exception {
        AtomicReference<String> caminhoRecebido = new AtomicReference<>();

        comServidorComSufixo(barrasFinais, exchange -> {
            caminhoRecebido.set(exchange.getRequestURI().getPath());
            responder(exchange, 404, "");
        }, fonte -> {
            ResultadoConsulta resultado = fonte.buscar(CNPJ);

            assertInstanceOf(ResultadoConsulta.NaoEncontrada.class, resultado);
            assertEquals("/api/cnpj/v1/12ABC34501DE35", caminhoRecebido.get());
        });
    }

    @Test
    void criarHttpClientDefineConnectTimeout() {
        HttpClient client = BrasilApiFonteCnpj.criarHttpClient(Duration.ofSeconds(2));

        assertEquals(Optional.of(Duration.ofSeconds(2)), client.connectTimeout());
    }

    @Test
    void criarHttpClientRejeitaNull() {
        assertThrows(NullPointerException.class, () -> BrasilApiFonteCnpj.criarHttpClient(null));
    }

    @Test
    void cnaeAusenteViraNull() throws Exception {
        String json = """
                {
                  "cnpj": "12ABC34501DE35",
                  "razao_social": "Empresa Exemplo Ltda",
                  "situacao_cadastral": 2
                }
                """;

        comServidor(exchange -> responder(exchange, 200, json), fonte -> {
            EmpresaCnpj empresa = assertInstanceOf(
                    ResultadoConsulta.Encontrada.class, fonte.buscar(CNPJ)).empresa();

            assertNull(empresa.cnaeFiscal());
        });
    }

    @Test
    void cnaeNullViraNull() throws Exception {
        String json = """
                {
                  "cnpj": "12ABC34501DE35",
                  "razao_social": "Empresa Exemplo Ltda",
                  "situacao_cadastral": 2,
                  "cnae_fiscal": null
                }
                """;

        comServidor(exchange -> responder(exchange, 200, json), fonte -> {
            EmpresaCnpj empresa = assertInstanceOf(
                    ResultadoConsulta.Encontrada.class, fonte.buscar(CNPJ)).empresa();

            assertNull(empresa.cnaeFiscal());
        });
    }

    @Test
    void situacaoAusenteViraDesconhecida() throws Exception {
        String json = """
                {
                  "cnpj": "12ABC34501DE35",
                  "razao_social": "Empresa Exemplo Ltda"
                }
                """;

        comServidor(exchange -> responder(exchange, 200, json), fonte -> {
            EmpresaCnpj empresa = assertInstanceOf(
                    ResultadoConsulta.Encontrada.class, fonte.buscar(CNPJ)).empresa();

            assertEquals(SituacaoCadastral.DESCONHECIDA, empresa.situacaoCadastral());
        });
    }

    @Test
    void situacaoNullViraDesconhecida() throws Exception {
        String json = """
                {
                  "cnpj": "12ABC34501DE35",
                  "razao_social": "Empresa Exemplo Ltda",
                  "situacao_cadastral": null
                }
                """;

        comServidor(exchange -> responder(exchange, 200, json), fonte -> {
            EmpresaCnpj empresa = assertInstanceOf(
                    ResultadoConsulta.Encontrada.class, fonte.buscar(CNPJ)).empresa();

            assertEquals(SituacaoCadastral.DESCONHECIDA, empresa.situacaoCadastral());
        });
    }

    @Test
    void buscarRejeitaCnpjNulo() {
        BrasilApiFonteCnpj fonte = new BrasilApiFonteCnpj(
                BrasilApiFonteCnpj.criarHttpClient(CONNECT_TIMEOUT),
                URI.create("http://localhost:1"),
                REQUEST_TIMEOUT);

        assertThrows(NullPointerException.class, () -> fonte.buscar(null));
    }

    @ParameterizedTest
    @CsvSource({"400", "429", "500"})
    void errosHttpNaoExpoemDetalhes(int status) throws Exception {
        comServidor(exchange -> responder(exchange, status, "segredo-interno"), fonte -> {
            ResultadoConsulta.Indisponivel indisponivel =
                    assertInstanceOf(ResultadoConsulta.Indisponivel.class, fonte.buscar(CNPJ));

            assertMotivoSemDetalhes(indisponivel.motivo());
        });
    }

    @Test
    void timeoutNaoExpoeDetalhes() throws Exception {
        comServidor(exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            responder(exchange, 200, "segredo-interno");
        }, fonte -> {
            ResultadoConsulta.Indisponivel indisponivel =
                    assertInstanceOf(ResultadoConsulta.Indisponivel.class, fonte.buscar(CNPJ));

            assertMotivoSemDetalhes(indisponivel.motivo());
        });
    }

    @Test
    void servidorParadoNaoExpoeDetalhes() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        URI baseUrl = baseUrl(server);
        server.stop(0);

        BrasilApiFonteCnpj fonte = new BrasilApiFonteCnpj(
                BrasilApiFonteCnpj.criarHttpClient(CONNECT_TIMEOUT), baseUrl, REQUEST_TIMEOUT);

        ResultadoConsulta.Indisponivel indisponivel =
                assertInstanceOf(ResultadoConsulta.Indisponivel.class, fonte.buscar(CNPJ));

        assertMotivoSemDetalhes(indisponivel.motivo());
    }

    private static void assertMotivoSemDetalhes(String motivo) {
        assertFalse(motivo.contains("localhost"));
        assertFalse(motivo.contains("/api/cnpj"));
        assertFalse(motivo.contains("segredo-interno"));
    }

    @Test
    void interrupcaoRestauraFlagERetornaIndisponivel() throws Exception {
        comServidor(exchange -> responder(exchange, 200, JSON_COMPLETO), fonte -> {
            Thread.currentThread().interrupt();
            try {
                ResultadoConsulta.Indisponivel indisponivel =
                        assertInstanceOf(ResultadoConsulta.Indisponivel.class, fonte.buscar(CNPJ));

                assertEquals("InterruptedException", indisponivel.motivo());
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        });
    }

    private static void assertCnaeMapeado(String cnaeJson, String esperado) throws Exception {
        String json = jsonComCnae(cnaeJson);

        comServidor(exchange -> responder(exchange, 200, json), fonte -> {
            EmpresaCnpj empresa = assertInstanceOf(
                    ResultadoConsulta.Encontrada.class, fonte.buscar(CNPJ)).empresa();

            assertEquals(esperado, empresa.cnaeFiscal());
        });
    }

    private static void comServidorComSufixo(String sufixoBaseUrl, Handler handler, Cenario cenario) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handler.handle(exchange));
        server.start();
        try {
            URI base = URI.create(baseUrl(server).toString() + sufixoBaseUrl);
            BrasilApiFonteCnpj fonte = new BrasilApiFonteCnpj(
                    BrasilApiFonteCnpj.criarHttpClient(CONNECT_TIMEOUT), base, REQUEST_TIMEOUT);
            cenario.executar(fonte);
        } finally {
            server.stop(0);
        }
    }

    private static void comServidor(Handler handler, Cenario cenario) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handler.handle(exchange));
        server.start();
        try {
            BrasilApiFonteCnpj fonte = new BrasilApiFonteCnpj(
                    BrasilApiFonteCnpj.criarHttpClient(CONNECT_TIMEOUT), baseUrl(server), REQUEST_TIMEOUT);
            cenario.executar(fonte);
        } finally {
            server.stop(0);
        }
    }

    private static URI baseUrl(HttpServer server) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static void responder(HttpExchange exchange, int status, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        if (bytes.length == 0) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    private static String jsonComCnae(String cnaeJson) {
        return """
                {
                  "cnpj": "12ABC34501DE35",
                  "razao_social": "Empresa Exemplo Ltda",
                  "situacao_cadastral": 2,
                  "cnae_fiscal": %s
                }
                """.formatted(cnaeJson);
    }

    private static String jsonCom(String cnaeFiscal, Integer situacaoCadastral) {
        String cnae = cnaeFiscal == null ? "" : "\"cnae_fiscal\": " + cnaeFiscal + ",";
        String situacao = situacaoCadastral == null ? "" : "\"situacao_cadastral\": " + situacaoCadastral + ",";
        return """
                {
                  "cnpj": "12ABC34501DE35",
                  "razao_social": "Empresa Exemplo Ltda",
                  %s
                  %s
                  "municipio": "São Paulo",
                  "uf": "SP"
                }
                """.formatted(cnae, situacao);
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    @FunctionalInterface
    private interface Cenario {
        void executar(BrasilApiFonteCnpj fonte) throws Exception;
    }
}
