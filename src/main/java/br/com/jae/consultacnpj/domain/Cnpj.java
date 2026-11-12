package br.com.jae.consultacnpj.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public record Cnpj(String value) {

    private static final int LENGTH = 14;
    private static final Pattern FORMAT = Pattern.compile("[0-9A-Z]{12}[0-9]{2}");
    private static final int[] DV1_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] DV2_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    public Cnpj {
        if (value == null) {
            throw new IllegalArgumentException("CNPJ não pode ser nulo");
        }

        value = value.replace(".", "").replace("-", "").replace("/", "").replace(" ", "");

        // Rejeitar caracteres não ASCII antes de normalizar evita que toUpperCase(Locale.ROOT)
        // converta 'ı' (U+0131) em 'I' e 'ſ' (U+017F) em 'S', aceitando não ASCII como ASCII.
        if (!value.chars().allMatch(c -> c < 128)) {
            throw new IllegalArgumentException(
                    "CNPJ deve conter 12 caracteres alfanuméricos seguidos de 2 dígitos verificadores");
        }

        value = value.toUpperCase(Locale.ROOT);

        if (value.length() != LENGTH || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "CNPJ deve conter 12 caracteres alfanuméricos seguidos de 2 dígitos verificadores");
        }

        // Sequências repetidas podem satisfazer o cálculo do dígito verificador
        // (por exemplo, 00000000000000), mas não identificam empresas reais.
        // Rejeitamos explicitamente para não aceitar valores de preenchimento.
        if (isRepeatedSequence(value)) {
            throw new IllegalArgumentException("CNPJ não pode ser uma sequência repetida");
        }

        int dv1 = calculateDv(value, DV1_WEIGHTS, 0);
        int dv2 = calculateDv(value, DV2_WEIGHTS, dv1);

        if (value.charAt(12) - '0' != dv1 || value.charAt(13) - '0' != dv2) {
            throw new IllegalArgumentException("CNPJ com dígitos verificadores inválidos");
        }
    }

    private static boolean isRepeatedSequence(String value) {
        return value.chars().distinct().count() == 1;
    }

    private static int calculateDv(String value, int[] weights, int dv1) {
        int sum = 0;

        for (int i = 0; i < weights.length; i++) {
            int characterValue = i < 12 ? value.charAt(i) - 48 : dv1;
            sum += characterValue * weights[i];
        }

        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
