package br.com.jae.consultacnpj.infrastructure.config;

import br.com.jae.consultacnpj.application.port.in.ConsultarCnpjUseCase;
import br.com.jae.consultacnpj.application.port.out.FonteCnpj;
import br.com.jae.consultacnpj.application.service.ConsultarCnpjService;
import br.com.jae.consultacnpj.infrastructure.adapter.out.brasilapi.BrasilApiFonteCnpj;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BrasilApiProperties.class)
public class ConsultaCnpjConfig {

    @Bean
    public HttpClient httpClient(BrasilApiProperties properties) {
        return BrasilApiFonteCnpj.criarHttpClient(properties.getConnectTimeout());
    }

    @Bean
    public FonteCnpj brasilApiFonteCnpj(HttpClient httpClient, BrasilApiProperties properties) {
        return new BrasilApiFonteCnpj(
                httpClient, properties.getBaseUrl(), properties.getRequestTimeout());
    }

    // O serviço recebe a lista na ordem dos beans e decide a prioridade entre as fontes.
    @Bean
    public ConsultarCnpjUseCase consultarCnpjUseCase(List<FonteCnpj> fontes) {
        return new ConsultarCnpjService(fontes);
    }
}
