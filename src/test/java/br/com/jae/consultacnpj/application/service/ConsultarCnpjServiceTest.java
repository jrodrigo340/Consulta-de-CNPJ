package br.com.jae.consultacnpj.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.jae.consultacnpj.application.port.out.FonteCnpj;
import br.com.jae.consultacnpj.domain.Cnpj;
import br.com.jae.consultacnpj.domain.EmpresaCnpj;
import br.com.jae.consultacnpj.domain.ResultadoConsulta;
import br.com.jae.consultacnpj.domain.SituacaoCadastral;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsultarCnpjServiceTest {

    private static final Cnpj CNPJ = new Cnpj("12ABC34501DE35");
    private static final EmpresaCnpj EMPRESA = new EmpresaCnpj(
            CNPJ, "Razão social", null, SituacaoCadastral.ATIVA, "1234567", "Município", "UF");
    private static final Cnpj OUTRO_CNPJ = new Cnpj("11.222.333/0001-81");
    private static final EmpresaCnpj OUTRA_EMPRESA = new EmpresaCnpj(
            OUTRO_CNPJ, "Outra razão social", null, SituacaoCadastral.ATIVA, "7654321", "Outro município", "UF");

    @Test
    void primeiraEncontraNaoChamaSegunda() {
        FonteEncontrada primeira = new FonteEncontrada();
        FonteNaoEncontrada segunda = new FonteNaoEncontrada();
        ConsultarCnpjService service = new ConsultarCnpjService(List.of(primeira, segunda));

        ResultadoConsulta resultado = service.consultar(CNPJ);

        assertEquals(new ResultadoConsulta.Encontrada(EMPRESA), resultado);
        assertEquals(1, primeira.chamadas);
        assertEquals(0, segunda.chamadas);
    }

    @Test
    void primeiraIndisponivelSegundaEncontra() {
        FonteIndisponivelA primeira = new FonteIndisponivelA();
        FonteEncontrada segunda = new FonteEncontrada();
        ConsultarCnpjService service = new ConsultarCnpjService(List.of(primeira, segunda));

        ResultadoConsulta resultado = service.consultar(CNPJ);

        assertEquals(new ResultadoConsulta.Encontrada(EMPRESA), resultado);
        assertEquals(1, primeira.chamadas);
        assertEquals(1, segunda.chamadas);
    }

    @Test
    void primeiraNaoEncontraNaoChamaSegunda() {
        FonteNaoEncontrada primeira = new FonteNaoEncontrada();
        FonteEncontrada segunda = new FonteEncontrada();
        ConsultarCnpjService service = new ConsultarCnpjService(List.of(primeira, segunda));

        ResultadoConsulta resultado = service.consultar(CNPJ);

        assertEquals(new ResultadoConsulta.NaoEncontrada(), resultado);
        assertEquals(1, primeira.chamadas);
        assertEquals(0, segunda.chamadas);
    }

    @Test
    void todasIndisponiveisAgregaMotivosComNomeDeCadaFonte() {
        FonteIndisponivelA primeira = new FonteIndisponivelA();
        FonteIndisponivelB segunda = new FonteIndisponivelB();
        ConsultarCnpjService service = new ConsultarCnpjService(List.of(primeira, segunda));

        ResultadoConsulta resultado = service.consultar(CNPJ);

        ResultadoConsulta.Indisponivel indisponivel =
                assertInstanceOf(ResultadoConsulta.Indisponivel.class, resultado);
        assertTrue(indisponivel.motivo().contains("FonteIndisponivelA"));
        assertTrue(indisponivel.motivo().contains("sem rede"));
        assertTrue(indisponivel.motivo().contains("FonteIndisponivelB"));
        assertTrue(indisponivel.motivo().contains("timeout"));
    }

    @Test
    void excecaoNaPrimeiraViraIndisponivelETentaSegunda() {
        FonteComExcecao primeira = new FonteComExcecao();
        FonteEncontrada segunda = new FonteEncontrada();
        ConsultarCnpjService service = new ConsultarCnpjService(List.of(primeira, segunda));

        ResultadoConsulta resultado = service.consultar(CNPJ);

        assertEquals(new ResultadoConsulta.Encontrada(EMPRESA), resultado);
        assertEquals(1, primeira.chamadas);
        assertEquals(1, segunda.chamadas);
    }

    @Test
    void motivoDeExcecaoContemFonteTipoEMensagem() {
        ConsultarCnpjService service = new ConsultarCnpjService(List.of(new FonteComExcecao()));

        ResultadoConsulta.Indisponivel indisponivel = assertInstanceOf(
                ResultadoConsulta.Indisponivel.class, service.consultar(CNPJ));

        assertEquals("FonteComExcecao: IllegalStateException: timeout", indisponivel.motivo());
    }

    @Test
    void fonteQueRetornaNuloViraIndisponivelETentaProxima() {
        FonteNula primeira = new FonteNula();
        FonteEncontrada segunda = new FonteEncontrada();
        ConsultarCnpjService service = new ConsultarCnpjService(List.of(primeira, segunda));

        ResultadoConsulta resultado = service.consultar(CNPJ);

        assertEquals(new ResultadoConsulta.Encontrada(EMPRESA), resultado);
        assertEquals(1, primeira.chamadas);
        assertEquals(1, segunda.chamadas);
    }

    @Test
    void motivoDeFonteNulaInformaNomeDaFonte() {
        ConsultarCnpjService service = new ConsultarCnpjService(List.of(new FonteNula()));

        ResultadoConsulta.Indisponivel indisponivel = assertInstanceOf(
                ResultadoConsulta.Indisponivel.class, service.consultar(CNPJ));

        assertEquals("FonteNula retornou nulo", indisponivel.motivo());
    }

    @Test
    void fonteAnonimaGeraNomeNaoVazioNoMotivo() {
        FonteCnpj anonima = new FonteCnpj() {
            @Override
            public ResultadoConsulta buscar(Cnpj cnpj) {
                return new ResultadoConsulta.Indisponivel("sem rede");
            }
        };
        ConsultarCnpjService service = new ConsultarCnpjService(List.of(anonima));

        ResultadoConsulta.Indisponivel indisponivel = assertInstanceOf(
                ResultadoConsulta.Indisponivel.class, service.consultar(CNPJ));

        assertTrue(indisponivel.motivo().startsWith(anonima.getClass().getName()));
    }

    @Test
    void fonteComEmpresaDeOutroCnpjEIgnoradaETentaProxima() {
        FonteOutroCnpjA primeira = new FonteOutroCnpjA();
        FonteEncontrada segunda = new FonteEncontrada();
        ConsultarCnpjService service = new ConsultarCnpjService(List.of(primeira, segunda));

        ResultadoConsulta resultado = service.consultar(CNPJ);

        assertEquals(new ResultadoConsulta.Encontrada(EMPRESA), resultado);
        assertEquals(1, primeira.chamadas);
        assertEquals(1, segunda.chamadas);
    }

    @Test
    void todasComEmpresaDeOutroCnpjAgregaMotivosDeCadaFonte() {
        FonteOutroCnpjA primeira = new FonteOutroCnpjA();
        FonteOutroCnpjB segunda = new FonteOutroCnpjB();
        ConsultarCnpjService service = new ConsultarCnpjService(List.of(primeira, segunda));

        ResultadoConsulta resultado = service.consultar(CNPJ);

        ResultadoConsulta.Indisponivel indisponivel =
                assertInstanceOf(ResultadoConsulta.Indisponivel.class, resultado);
        assertTrue(indisponivel.motivo().contains("FonteOutroCnpjA"));
        assertTrue(indisponivel.motivo().contains("FonteOutroCnpjB"));
        assertTrue(indisponivel.motivo().contains("devolveu empresa de outro CNPJ"));
    }

    @Test
    void indisponivelComMotivoNuloUsaMotivoPadrao() {
        ResultadoConsulta.Indisponivel indisponivel = new ResultadoConsulta.Indisponivel(null);

        assertEquals("motivo não informado", indisponivel.motivo());
    }

    @Test
    void rejeitaListaVazia() {
        assertThrows(IllegalArgumentException.class, () -> new ConsultarCnpjService(List.of()));
    }

    @Test
    void rejeitaListaNula() {
        assertThrows(IllegalArgumentException.class, () -> new ConsultarCnpjService(null));
    }

    @Test
    void consultarRejeitaCnpjNulo() {
        FonteEncontrada fonte = new FonteEncontrada();
        ConsultarCnpjService service = new ConsultarCnpjService(List.of(fonte));

        assertThrows(NullPointerException.class, () -> service.consultar(null));
        assertEquals(0, fonte.chamadas);
    }

    private static final class FonteEncontrada implements FonteCnpj {
        private int chamadas;

        @Override
        public ResultadoConsulta buscar(Cnpj cnpj) {
            chamadas++;
            return new ResultadoConsulta.Encontrada(EMPRESA);
        }
    }

    private static final class FonteNaoEncontrada implements FonteCnpj {
        private int chamadas;

        @Override
        public ResultadoConsulta buscar(Cnpj cnpj) {
            chamadas++;
            return new ResultadoConsulta.NaoEncontrada();
        }
    }

    private static final class FonteOutroCnpjA implements FonteCnpj {
        private int chamadas;

        @Override
        public ResultadoConsulta buscar(Cnpj cnpj) {
            chamadas++;
            return new ResultadoConsulta.Encontrada(OUTRA_EMPRESA);
        }
    }

    private static final class FonteOutroCnpjB implements FonteCnpj {
        private int chamadas;

        @Override
        public ResultadoConsulta buscar(Cnpj cnpj) {
            chamadas++;
            return new ResultadoConsulta.Encontrada(OUTRA_EMPRESA);
        }
    }

    private static final class FonteIndisponivelA implements FonteCnpj {
        private int chamadas;

        @Override
        public ResultadoConsulta buscar(Cnpj cnpj) {
            chamadas++;
            return new ResultadoConsulta.Indisponivel("sem rede");
        }
    }

    private static final class FonteIndisponivelB implements FonteCnpj {
        private int chamadas;

        @Override
        public ResultadoConsulta buscar(Cnpj cnpj) {
            chamadas++;
            return new ResultadoConsulta.Indisponivel("timeout");
        }
    }

    private static final class FonteNula implements FonteCnpj {
        private int chamadas;

        @Override
        public ResultadoConsulta buscar(Cnpj cnpj) {
            chamadas++;
            return null;
        }
    }

    private static final class FonteComExcecao implements FonteCnpj {
        private int chamadas;

        @Override
        public ResultadoConsulta buscar(Cnpj cnpj) {
            chamadas++;
            throw new IllegalStateException("timeout");
        }
    }
}
