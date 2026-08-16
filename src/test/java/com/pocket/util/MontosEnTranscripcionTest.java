package com.pocket.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MontosEnTranscripcionTest {

    private static boolean menciona(String texto, String monto) {
        return MontosEnTranscripcion.menciona(texto, new BigDecimal(monto));
    }

    @Nested
    @DisplayName("Montos escritos en dígitos")
    class EnDigitos {

        @ParameterizedTest(name = "\"{0}\" menciona {1}")
        @CsvSource({
                "Gasté 5000 en facturas,        5000",
                "Gasté 5.000 en facturas,       5000",
                "Gasté 150.000 en el súper,     150000",
                "Pagué 1.500.000 por el auto,   1500000",
                "Salió 20.000 de nafta,         20000"
        })
        void reconoceDigitosConYSinSeparadorDeMiles(String texto, String monto) {
            assertThat(menciona(texto, monto)).isTrue();
        }

        @Test
        @DisplayName("La coma separa decimales, no miles")
        void laComaEsDecimal() {
            assertThat(menciona("Salió 5,50 el kilo", "5.50")).isTrue();
        }

        @Test
        @DisplayName("El monto se compara por valor, no por escala: 5000 matchea 5000.00")
        void comparaPorValorNoPorEscala() {
            assertThat(menciona("Gasté 5.000 en facturas", "5000.00")).isTrue();
        }
    }

    @Nested
    @DisplayName("Montos dichos en letras")
    class EnLetras {

        @ParameterizedTest(name = "\"{0}\" menciona {1}")
        @CsvSource({
                "Gasté cinco mil en el kiosco,                        5000",
                "Gasté veinte mil de nafta,                           20000",
                "Ciento cincuenta mil en el súper,                    150000",
                "Me salió treinta y cinco mil,                        35000",
                "Pagué un millón por el auto,                         1000000",
                "Salieron quinientos pesos,                           500",
                "Gasté dieciocho mil quinientos,                      18500",
                "Costó doscientos cincuenta mil,                      250000"
        })
        void reconoceNumerosEnPalabras(String texto, String monto) {
            assertThat(menciona(texto, monto)).isTrue();
        }

        @Test
        @DisplayName("Los acentos no importan: \"millón\" y \"millon\" son lo mismo")
        void ignoraAcentos() {
            assertThat(menciona("Pagué un millon por el auto", "1000000")).isTrue();
        }
    }

    @Nested
    @DisplayName("Lunfardo: luca y palo")
    class Lunfardo {

        @ParameterizedTest(name = "\"{0}\" menciona {1}")
        @CsvSource({
                "Gasté cinco lucas en facturas,     5000",
                "Me costó 20 lucas,                 20000",
                "Salió una luca,                    1000",
                "Pagué dos palos por el depto,      2000000",
                "Fueron cinco lucas y media,        5500",
                "Gasté 18 lucas y media,            18500"
        })
        void reconoceLucasYPalos(String texto, String monto) {
            assertThat(menciona(texto, monto)).isTrue();
        }
    }

    @Nested
    @DisplayName("Varios montos en una misma frase")
    class VariosMontos {

        private static final String FRASE =
                "Gasté cinco lucas en facturas, veinte mil de nafta y "
                        + "ciento cincuenta mil en el súper con crédito en tres cuotas.";

        @ParameterizedTest(name = "encuentra {0}")
        @ValueSource(strings = {"5000", "20000", "150000"})
        void separaLosMontosEnLugarDeSumarlos(String monto) {
            assertThat(menciona(FRASE, monto)).isTrue();
        }

        @Test
        @DisplayName("No inventa un monto que sea la suma de los otros")
        void noProduceLaSumaDeTodos() {
            assertThat(menciona(FRASE, "175000")).isFalse();
        }
    }

    @Nested
    @DisplayName("Casos que tienen que dar false")
    class NoMenciona {

        @Test
        @DisplayName("Un monto que no está en el texto")
        void montoAusente() {
            assertThat(menciona("Gasté cinco mil en facturas", "150000")).isFalse();
        }

        @Test
        @DisplayName("Un monto parecido pero distinto")
        void montoParecido() {
            assertThat(menciona("Gasté 5.000 en facturas", "50000")).isFalse();
        }

        @ParameterizedTest
        @DisplayName("Sin texto no hay nada que verificar")
        @ValueSource(strings = {"", "   "})
        void transcripcionVacia(String texto) {
            assertThat(menciona(texto, "5000")).isFalse();
        }

        @Test
        @DisplayName("Transcripción null")
        void transcripcionNull() {
            assertThat(MontosEnTranscripcion.menciona(null, new BigDecimal("5000"))).isFalse();
        }

        @Test
        @DisplayName("Monto null")
        void montoNull() {
            assertThat(MontosEnTranscripcion.menciona("Gasté 5000", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("extraer()")
    class Extraer {

        @Test
        @DisplayName("Devuelve todos los importes del texto")
        void devuelveTodosLosImportes() {
            assertThat(MontosEnTranscripcion.extraer("Gasté cinco lucas y veinte mil de nafta"))
                    .contains(new BigDecimal("5000"), new BigDecimal("20000"));
        }

        @Test
        @DisplayName("Un texto sin números devuelve conjunto vacío")
        void textoSinNumeros() {
            assertThat(MontosEnTranscripcion.extraer("No se entendió nada de lo que dijo")).isEmpty();
        }
    }
}
