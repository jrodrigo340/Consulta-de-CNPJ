package br.com.jae.consultacnpj.infrastructure.adapter.out.brasilapi;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import br.com.jae.consultacnpj.domain.Cnpj;
import br.com.jae.consultacnpj.domain.ResultadoConsulta;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Teste manual contra a BrasilAPI real, desativado por padrão.
 *
 * <p>Para rodar no PowerShell:
 *
 * <pre>
 * $env:CONSULTA_REAL="1"; .\gradlew.bat cleanTest test --tests "*ConsultaRealManualTest" -i
 * </pre>
 *
 * <p>O {@code -i} é necessário para ver a saída e o {@code cleanTest} força a reexecução.
 * Nenhum outro teste acessa a internet.
 */
@EnabledIfEnvironmentVariable(named = "CONSULTA_REAL", matches = "1")
class ConsultaRealManualTest {

    private static final URI BASE_URL = URI.create("https://brasilapi.com.br");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void consultaCnpjsReais() throws Exception {
        BrasilApiFonteCnpj fonte = new BrasilApiFonteCnpj(
                BrasilApiFonteCnpj.criarHttpClient(CONNECT_TIMEOUT), BASE_URL, REQUEST_TIMEOUT);
        HttpClient httpClient = BrasilApiFonteCnpj.criarHttpClient(CONNECT_TIMEOUT);
        List<String> cnpjs = List.of("00000000000191", "11222333000181", "12ABC34501DE35");

        for (int i = 0; i < cnpjs.size(); i++) {
            Cnpj cnpj = new Cnpj(cnpjs.get(i));

            System.out.println("=== " + cnpj.value() + " ===");
            ResultadoConsulta resultado = fonte.buscar(cnpj);
            System.out.println(resultado);
            assertNotNull(resultado);

            diagnosticar(httpClient, cnpj);

            if (i < cnpjs.size() - 1) {
                Thread.sleep(1000);
            }
        }
    }

    private static void diagnosticar(HttpClient httpClient, Cnpj cnpj) {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(BASE_URL + "/api/cnpj/v1/" + cnpj.value()))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "consulta-cnpj-manual")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            System.out.println("HTTP " + response.statusCode());
            String corpo = response.body();
            if (corpo != null) {
                System.out.println(corpo.substring(0, Math.min(2000, corpo.length())));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Falha na requisição de diagnóstico: " + e);
        } catch (IOException e) {
            System.out.println("Falha na requisição de diagnóstico: " + e);
        }
    }
}
