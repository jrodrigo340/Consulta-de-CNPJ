package br.com.jae.consultacnpj.application.port.in;

import br.com.jae.consultacnpj.domain.Cnpj;
import br.com.jae.consultacnpj.domain.ResultadoConsulta;

public interface ConsultarCnpjUseCase {

    ResultadoConsulta consultar(Cnpj cnpj);
}
