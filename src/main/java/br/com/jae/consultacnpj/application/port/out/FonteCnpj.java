package br.com.jae.consultacnpj.application.port.out;

import br.com.jae.consultacnpj.domain.Cnpj;
import br.com.jae.consultacnpj.domain.ResultadoConsulta;

public interface FonteCnpj {

    ResultadoConsulta buscar(Cnpj cnpj);
}
