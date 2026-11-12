package br.com.jae.consultacnpj.domain;

import java.util.Objects;

public record EmpresaCnpj(
        Cnpj cnpj,
        String razaoSocial,
        String nomeFantasia,
        SituacaoCadastral situacaoCadastral,
        String cnaeFiscal,
        String municipio,
        String uf) {

    public EmpresaCnpj {
        Objects.requireNonNull(cnpj, "CNPJ não pode ser nulo");
        Objects.requireNonNull(razaoSocial, "Razão social não pode ser nula");
        Objects.requireNonNull(situacaoCadastral, "Situação cadastral não pode ser nula");
    }
}
