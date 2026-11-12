package br.com.jae.consultacnpj.domain;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EmpresaCnpjTest {

    private static final Cnpj CNPJ = new Cnpj("12ABC34501DE35");

    @Test
    void aceitaNomeFantasiaNulo() {
        EmpresaCnpj empresa = new EmpresaCnpj(
                CNPJ, "Razão social", null, SituacaoCadastral.ATIVA, "1234567", "Município", "UF");

        assertNull(empresa.nomeFantasia());
    }

    @Test
    void aceitaCnaeMunicipioEUfNulos() {
        EmpresaCnpj empresa = new EmpresaCnpj(
                CNPJ, "Razão social", null, SituacaoCadastral.ATIVA, null, null, null);

        assertNull(empresa.cnaeFiscal());
        assertNull(empresa.municipio());
        assertNull(empresa.uf());
    }

    @Test
    void rejeitaCnpjNulo() {
        assertThrows(NullPointerException.class, () -> new EmpresaCnpj(
                null, "Razão social", null, SituacaoCadastral.ATIVA, "1234567", "Município", "UF"));
    }

    @Test
    void rejeitaRazaoSocialNula() {
        assertThrows(NullPointerException.class, () -> new EmpresaCnpj(
                CNPJ, null, null, SituacaoCadastral.ATIVA, "1234567", "Município", "UF"));
    }

    @Test
    void rejeitaSituacaoCadastralNula() {
        assertThrows(NullPointerException.class, () -> new EmpresaCnpj(
                CNPJ, "Razão social", null, null, "1234567", "Município", "UF"));
    }
}
