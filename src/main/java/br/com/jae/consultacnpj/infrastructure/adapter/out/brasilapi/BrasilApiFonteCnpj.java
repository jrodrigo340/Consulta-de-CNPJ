package br.com.jae.consultacnpj.infrastructure.adapter.out.brasilapi;

import br.com.jae.consultacnpj.application.port.out.FonteCnpj;
import br.com.jae.consultacnpj.domain.Cnpj;
import br.com.jae.consultacnpj.domain.EmpresaCnpj;
import br.com.jae.consultacnpj.domain.ResultadoConsulta;
import br.com.jae.consultacnpj.domain.SituacaoCadastral;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class BrasilApiFonteCnpj implements FonteCnpj {

    private static final String CAMINHO = "/api/cnpj/v1/";
    private static final Pattern CNAE_TEXTO = Pattern.compile("\\d{1,7}");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final URI baseUrl;
    private final Duration timeoutRequisicao;

    public BrasilApiFonteCnpj(HttpClient httpClient, URI baseUrl, Duration timeoutRequisicao) {
        this.httpClient = Objects.requireNonNull(httpClient, "HttpClient não pode ser nulo");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl não pode ser nula");
        this.timeoutRequisicao = Objects.requireNonNull(timeoutRequisicao, "timeout não pode ser nulo");

        String esquema = this.baseUrl.getScheme();
        if (esquema == null
                || !(esquema.equalsIgnoreCase("http") || esquema.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("baseUrl deve usar http ou https");
        }
        if (this.baseUrl.getHost() == null) {
            throw new IllegalArgumentException("baseUrl deve ter host");
        }
    }

    public static HttpClient criarHttpClient(Duration connectTimeout) {
        // connectTimeout limita apenas a abertura da conexão, enquanto o timeout da
        // requisição limita a espera pela resposta; são limites diferentes e ambos
        // são necessários para não ficar bloqueado em nenhuma das etapas.
        return HttpClient.newBuilder()
                .connectTimeout(Objects.requireNonNull(connectTimeout, "connectTimeout não pode ser nulo"))
                .build();
    }

    @Override
    public ResultadoConsulta buscar(Cnpj cnpj) {
        Objects.requireNonNull(cnpj, "CNPJ não pode ser nulo");

        HttpRequest request = HttpRequest.newBuilder(montarUri(cnpj))
                .timeout(timeoutRequisicao)
                .header("Accept", "application/json")
                .header("User-Agent", "consulta-cnpj")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            // Restaurar a flag é necessário para que a interrupção não seja engolida:
            // assim quem chamou (ou o pool de threads) ainda consegue detectá-la.
            Thread.currentThread().interrupt();
            return new ResultadoConsulta.Indisponivel(e.getClass().getSimpleName());
        } catch (IOException e) {
            return new ResultadoConsulta.Indisponivel(e.getClass().getSimpleName());
        }

        int status = response.statusCode();
        if (status == 404) {
            return new ResultadoConsulta.NaoEncontrada();
        }
        if (status == 429) {
            return new ResultadoConsulta.Indisponivel("limite de requisições da BrasilAPI");
        }
        if (status != 200) {
            return new ResultadoConsulta.Indisponivel("HTTP " + status);
        }

        String corpo = response.body();
        if (corpo == null || corpo.isBlank()) {
            return new ResultadoConsulta.Indisponivel("resposta inválida");
        }

        try {
            JsonNode raiz = MAPPER.readTree(corpo);
            if (raiz == null || !raiz.isObject()) {
                return new ResultadoConsulta.Indisponivel("resposta inválida");
            }
            return mapear(raiz);
        } catch (JacksonException e) {
            return new ResultadoConsulta.Indisponivel("resposta inválida");
        }
    }

    private URI montarUri(Cnpj cnpj) {
        String base = baseUrl.toString();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        // cnpj.value() já foi validado como [0-9A-Z]{14}, então pode ir direto no path
        // sem necessidade de encode.
        return URI.create(base + CAMINHO + cnpj.value());
    }

    private static ResultadoConsulta mapear(JsonNode raiz) {
        Cnpj cnpj;
        try {
            cnpj = new Cnpj(texto(raiz, "cnpj"));
        } catch (IllegalArgumentException e) {
            return new ResultadoConsulta.Indisponivel("resposta com CNPJ inválido");
        }

        String razaoSocial = texto(raiz, "razao_social");
        if (razaoSocial == null || razaoSocial.isBlank()) {
            return new ResultadoConsulta.Indisponivel("resposta inválida");
        }

        String nomeFantasia = texto(raiz, "nome_fantasia");
        if (nomeFantasia != null && nomeFantasia.isBlank()) {
            nomeFantasia = null;
        }

        SituacaoCadastral situacao = situacao(raiz.path("situacao_cadastral"));
        String cnaeFiscal = cnaeFiscal(raiz.get("cnae_fiscal"));
        String municipio = texto(raiz, "municipio");
        String uf = texto(raiz, "uf");

        return new ResultadoConsulta.Encontrada(new EmpresaCnpj(
                cnpj, razaoSocial, nomeFantasia, situacao, cnaeFiscal, municipio, uf));
    }

    private static String texto(JsonNode raiz, String campo) {
        JsonNode node = raiz.get(campo);
        if (node == null || !node.isTextual()) {
            return null;
        }
        return node.asString();
    }

    private static SituacaoCadastral situacao(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return SituacaoCadastral.DESCONHECIDA;
        }
        return switch (node.asInt(-1)) {
            case 1 -> SituacaoCadastral.NULA;
            case 2 -> SituacaoCadastral.ATIVA;
            case 3 -> SituacaoCadastral.SUSPENSA;
            case 4 -> SituacaoCadastral.INAPTA;
            case 8 -> SituacaoCadastral.BAIXADA;
            default -> SituacaoCadastral.DESCONHECIDA;
        };
    }

    private static String cnaeFiscal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        int valor;
        if (node.isIntegralNumber() && node.canConvertToInt()) {
            // canConvertToInt evita que inteiros maiores que int sejam truncados em silêncio.
            valor = node.asInt();
        } else if (node.isTextual() && CNAE_TEXTO.matcher(node.asString()).matches()) {
            valor = Integer.parseInt(node.asString());
        } else {
            return null;
        }

        if (valor < 0 || valor > 9_999_999) {
            return null;
        }
        return String.format(Locale.ROOT, "%07d", valor);
    }
}
