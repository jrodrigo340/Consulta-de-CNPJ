package br.com.jae.consultacnpj.application.service;

import br.com.jae.consultacnpj.application.port.in.ConsultarCnpjUseCase;
import br.com.jae.consultacnpj.application.port.out.FonteCnpj;
import br.com.jae.consultacnpj.domain.Cnpj;
import br.com.jae.consultacnpj.domain.ResultadoConsulta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ConsultarCnpjService implements ConsultarCnpjUseCase {

    private final List<FonteCnpj> fontes;

    public ConsultarCnpjService(List<FonteCnpj> fontes) {
        if (fontes == null || fontes.isEmpty()) {
            throw new IllegalArgumentException("Lista de fontes não pode ser nula ou vazia");
        }
        this.fontes = List.copyOf(fontes);
    }

    @Override
    public ResultadoConsulta consultar(Cnpj cnpj) {
        Objects.requireNonNull(cnpj, "CNPJ não pode ser nulo");

        List<String> motivos = new ArrayList<>();

        for (FonteCnpj fonte : fontes) {
            ResultadoConsulta resultado;
            try {
                resultado = fonte.buscar(cnpj);
            } catch (RuntimeException e) {
                // Uma falha isolada de fonte não deve derrubar a consulta inteira.
                motivos.add(descreverFalha(fonte, e));
                continue;
            }

            if (resultado == null) {
                // O switch com pattern matching não aceita null; tratamos como indisponibilidade.
                motivos.add(nomeDaFonte(fonte) + " retornou nulo");
                continue;
            }

            switch (resultado) {
                case ResultadoConsulta.Encontrada encontrada -> {
                    if (!encontrada.empresa().cnpj().equals(cnpj)) {
                        motivos.add(nomeDaFonte(fonte) + " devolveu empresa de outro CNPJ");
                        continue;
                    }
                    return encontrada;
                }
                case ResultadoConsulta.NaoEncontrada naoEncontrada -> {
                    return naoEncontrada;
                }
                case ResultadoConsulta.Indisponivel indisponivel ->
                        motivos.add(nomeDaFonte(fonte) + ": " + indisponivel.motivo());
            }
        }

        return new ResultadoConsulta.Indisponivel(String.join("; ", motivos));
    }

    private static String descreverFalha(FonteCnpj fonte, RuntimeException e) {
        String motivo = nomeDaFonte(fonte) + ": " + e.getClass().getSimpleName();
        if (e.getMessage() != null) {
            motivo += ": " + e.getMessage();
        }
        return motivo;
    }

    private static String nomeDaFonte(FonteCnpj fonte) {
        String nome = fonte.getClass().getSimpleName();
        return nome.isEmpty() ? fonte.getClass().getName() : nome;
    }
}
