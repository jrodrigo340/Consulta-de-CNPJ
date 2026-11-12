package br.com.jae.consultacnpj.infrastructure.adapter.in.web;

public record CnpjResponse(
        String cnpj,
        String razaoSocial,
        String nomeFantasia,
        String situacaoCadastral,
        String cnaeFiscal,
        String municipio,
        String uf) {
}
