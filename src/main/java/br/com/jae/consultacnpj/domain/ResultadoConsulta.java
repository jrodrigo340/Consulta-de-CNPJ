package br.com.jae.consultacnpj.domain;

public sealed interface ResultadoConsulta {

    record Encontrada(EmpresaCnpj empresa) implements ResultadoConsulta {
    }

    record NaoEncontrada() implements ResultadoConsulta {
    }

    record Indisponivel(String motivo) implements ResultadoConsulta {
        public Indisponivel {
            if (motivo == null) {
                motivo = "motivo não informado";
            }
        }
    }
}
