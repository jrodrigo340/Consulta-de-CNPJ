package br.com.jae.consultacnpj.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CnpjTest {

    @Test
    void aceitaCnpjAlfanumericoOficialDaReceita() {
        Cnpj cnpj = new Cnpj("12.ABC.345/01DE-35");

        assertEquals("12ABC34501DE35", cnpj.value());
    }

    @Test
    void rejeitaCnpjAlfanumericoComDvErrado() {
        assertThrows(IllegalArgumentException.class, () -> new Cnpj("12.ABC.345/01DE-36"));
    }

    @Test
    void aceitaCnpjNumericoValidoConferidoManualmente() {
        // Base 112223330001:
        // DV1: 1*5+1*4+2*3+2*2+2*9+3*8+3*7+3*6+0*5+0*4+0*3+1*2 = 102
        //      102 % 11 = 3; 11 - 3 = 8.
        // DV2: 1*6+1*5+2*4+2*3+2*2+3*9+3*8+3*7+0*6+0*5+0*4+1*3+8*2 = 120
        //      120 % 11 = 10; 11 - 10 = 1.
        Cnpj cnpj = new Cnpj("11.222.333/0001-81");

        assertEquals("11222333000181", cnpj.value());
    }

    @Test
    void formatadoESemMascaraProduzemOMesmoResultado() {
        assertEquals(new Cnpj("12.ABC.345/01DE-35"), new Cnpj("12ABC34501DE35"));
        assertEquals(new Cnpj("11.222.333/0001-81"), new Cnpj("11222333000181"));
    }

    @Test
    void aceitaMinusculasENormalizaParaMaiusculas() {
        Cnpj cnpj = new Cnpj("12.abc.345/01de-35");

        assertEquals("12ABC34501DE35", cnpj.value());
    }

    @Test
    void aceitaCnpjComLetraIConferidoManualmente() {
        // Base 12ABC34501DI:
        // DV1: 1*5+2*4+17*3+18*2+19*9+3*8+4*7+5*6+0*5+1*4+20*3+25*2 = 467
        //      467 % 11 = 5; 11 - 5 = 6.
        // DV2: 1*6+2*5+17*4+18*3+19*2+3*9+4*8+5*7+0*6+1*5+20*4+25*3+6*2 = 442
        //      442 % 11 = 2; 11 - 2 = 9.
        Cnpj cnpj = new Cnpj("12.ABC.345/01DI-69");

        assertEquals("12ABC34501DI69", cnpj.value());
    }

    @Test
    void aceitaCnpjComLetraSConferidoManualmente() {
        // Base 12ABC34501DS:
        // DV1: 1*5+2*4+17*3+18*2+19*9+3*8+4*7+5*6+0*5+1*4+20*3+35*2 = 487
        //      487 % 11 = 3; 11 - 3 = 8.
        // DV2: 1*6+2*5+17*4+18*3+19*2+3*9+4*8+5*7+0*6+1*5+20*4+35*3+8*2 = 476
        //      476 % 11 = 3; 11 - 3 = 8.
        Cnpj cnpj = new Cnpj("12.ABC.345/01DS-88");

        assertEquals("12ABC34501DS88", cnpj.value());
    }

    @Test
    void rejeitaLetraIMinusculaTurcaNoLugarDeI() {
        assertThrows(IllegalArgumentException.class, () -> new Cnpj("12.ABC.345/01D\u0131-69"));
    }

    @Test
    void rejeitaLetraSLongaNoLugarDeS() {
        assertThrows(IllegalArgumentException.class, () -> new Cnpj("12.ABC.345/01D\u017F-88"));
    }

    @Test
    void aceitaMinusculasAsciiComLetrasIeS() {
        assertEquals(new Cnpj("12ABC34501DI69"), new Cnpj("12.abc.345/01di-69"));
        assertEquals(new Cnpj("12ABC34501DS88"), new Cnpj("12.abc.345/01ds-88"));
    }

    @Test
    void aceitaEspacoComumComoSeparador() {
        Cnpj cnpj = new Cnpj("12 ABC 345 01 DE 35");

        assertEquals("12ABC34501DE35", cnpj.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\t", "\n", "\u000B", "\f", "\r"})
    void rejeitaWhitespaceQueNaoSejaEspacoComum(String whitespace) {
        assertThrows(IllegalArgumentException.class, () -> new Cnpj("12ABC34501DE" + whitespace + "35"));
    }

    @Test
    void rejeitaNulo() {
        assertThrows(IllegalArgumentException.class, () -> new Cnpj(null));
    }

    @Test
    void rejeitaVazio() {
        assertThrows(IllegalArgumentException.class, () -> new Cnpj(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12ABC34501DE3",
            "12ABC34501DE355",
            "12ABC34501DEA5",
            "12ABC34501DE3A",
            "12ABC34501DE3!",
            "12ABC34501DE@5"
    })
    void rejeitaTamanhoInvalidoLetrasNosDvsECaracteresEspeciais(String value) {
        assertThrows(IllegalArgumentException.class, () -> new Cnpj(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "00000000000000",
            "11111111111111",
            "22222222222222",
            "33333333333333",
            "44444444444444",
            "55555555555555",
            "66666666666666",
            "77777777777777",
            "88888888888888",
            "99999999999999"
    })
    void rejeitaSequenciasRepetidasExplicitamente(String value) {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> new Cnpj(value));

        assertTrue(exception.getMessage().contains("sequência repetida"));
    }
}
