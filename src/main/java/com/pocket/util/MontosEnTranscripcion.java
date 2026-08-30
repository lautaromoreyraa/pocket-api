package com.pocket.util;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Extrae los importes que una transcripción menciona, en cualquiera de las
 * formas en que se dicen: "5000", "5.000", "cinco mil" o "cinco lucas".
 *
 * Existe para detectar respuestas fabricadas de la IA. Un modelo que no puede
 * leer el audio puede igual devolver un JSON válido con gastos verosímiles; si
 * el monto que reporta no aparece por ningún lado en lo que él mismo
 * transcribió, ese número no salió del audio.
 */
public final class MontosEnTranscripcion {

    /** Números que se dicen con una sola palabra, más los cientos. */
    private static final Map<String, Long> PALABRAS = Map.ofEntries(
            Map.entry("cero", 0L), Map.entry("un", 1L), Map.entry("uno", 1L), Map.entry("una", 1L),
            Map.entry("dos", 2L), Map.entry("tres", 3L), Map.entry("cuatro", 4L),
            Map.entry("cinco", 5L), Map.entry("seis", 6L), Map.entry("siete", 7L),
            Map.entry("ocho", 8L), Map.entry("nueve", 9L), Map.entry("diez", 10L),
            Map.entry("once", 11L), Map.entry("doce", 12L), Map.entry("trece", 13L),
            Map.entry("catorce", 14L), Map.entry("quince", 15L), Map.entry("dieciseis", 16L),
            Map.entry("diecisiete", 17L), Map.entry("dieciocho", 18L), Map.entry("diecinueve", 19L),
            Map.entry("veinte", 20L), Map.entry("veintiuno", 21L), Map.entry("veintiun", 21L),
            Map.entry("veintidos", 22L), Map.entry("veintitres", 23L), Map.entry("veinticuatro", 24L),
            Map.entry("veinticinco", 25L), Map.entry("veintiseis", 26L), Map.entry("veintisiete", 27L),
            Map.entry("veintiocho", 28L), Map.entry("veintinueve", 29L), Map.entry("treinta", 30L),
            Map.entry("cuarenta", 40L), Map.entry("cincuenta", 50L), Map.entry("sesenta", 60L),
            Map.entry("setenta", 70L), Map.entry("ochenta", 80L), Map.entry("noventa", 90L),
            Map.entry("cien", 100L), Map.entry("ciento", 100L), Map.entry("doscientos", 200L),
            Map.entry("trescientos", 300L), Map.entry("cuatrocientos", 400L),
            Map.entry("quinientos", 500L), Map.entry("seiscientos", 600L),
            Map.entry("setecientos", 700L), Map.entry("ochocientos", 800L),
            Map.entry("novecientos", 900L));

    /**
     * "gamba" es cien en lunfardo. No tiene equivalente formal en la lista de
     * palabras porque nadie dice "gamba" como número suelto: siempre multiplica
     * ("dos gambas" = 200), igual que "luca" y "palo".
     */
    private static final Set<String> ESCALA_CIEN = Set.of("gamba", "gambas");

    /** "luca" es mil en lunfardo; el modelo suele transcribirlo tal cual. */
    private static final Set<String> ESCALA_MIL = Set.of("mil", "luca", "lucas");
    private static final Set<String> ESCALA_MILLON = Set.of("millon", "millones", "palo", "palos");

    /** "cinco lucas y media" = 5500: suma la mitad de la última escala aplicada. */
    private static final Set<String> MITAD = Set.of("medio", "media");

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal MIL = BigDecimal.valueOf(1000);
    private static final BigDecimal MILLON = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal DOS = BigDecimal.valueOf(2);

    private MontosEnTranscripcion() {
    }

    /**
     * ¿La transcripción menciona este importe, en alguna de sus formas?
     * Una transcripción vacía nunca menciona nada: sin texto contra el cual
     * contrastar, no hay forma de saber si el monto es real.
     */
    public static boolean menciona(String transcripcion, BigDecimal monto) {
        if (transcripcion == null || transcripcion.isBlank() || monto == null) {
            return false;
        }
        return extraer(transcripcion).stream().anyMatch(m -> m.compareTo(monto) == 0);
    }

    /** Todos los importes que aparecen en el texto, en dígitos o en letras. */
    public static Set<BigDecimal> extraer(String transcripcion) {
        Set<BigDecimal> encontrados = new HashSet<>();
        if (transcripcion == null || transcripcion.isBlank()) {
            return encontrados;
        }

        Acumulador acumulador = new Acumulador(encontrados);
        for (String token : normalizar(transcripcion).split("[^a-z0-9.,]+")) {
            acumulador.consumir(limpiarBordes(token));
        }
        acumulador.cerrar();

        return encontrados;
    }

    /** Minúsculas y sin acentos: "millón" y "millon" tienen que ser lo mismo. */
    private static String normalizar(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinAcentos.toLowerCase(Locale.ROOT);
    }

    /** Saca la puntuación pegada ("facturas," -> "facturas") sin romper "5.000". */
    private static String limpiarBordes(String token) {
        return token.replaceAll("^[.,]+", "").replaceAll("[.,]+$", "");
    }

    /**
     * Recorre los tokens acumulando el número que se está diciendo. Un token
     * que no es parte de un número cierra el que venía armándose: así
     * "cinco lucas en facturas, veinte mil de nafta" produce 5000 y 20000, y no
     * un único número gigante.
     */
    private static final class Acumulador {

        private final Set<BigDecimal> destino;

        /** Bloques ya cerrados por una escala ("ciento cincuenta mil" -> 150000). */
        private BigDecimal total = BigDecimal.ZERO;
        /** Lo que se viene sumando y todavía no fue multiplicado por una escala. */
        private BigDecimal parcial = BigDecimal.ZERO;
        /** Última escala aplicada, para resolver "y media". */
        private BigDecimal ultimaEscala = BigDecimal.ZERO;
        private boolean enNumero;
        /**
         * Vimos una "y" y todavía no sabemos qué significa. Une en "treinta y
         * cinco" y en "cinco lucas y media", pero separa dos importes distintos
         * en "cinco lucas y veinte mil". Solo el token siguiente lo define.
         */
        private boolean pendienteY;

        private Acumulador(Set<BigDecimal> destino) {
            this.destino = destino;
        }

        private void consumir(String token) {
            if (token.isEmpty()) {
                return;
            }
            if (token.equals("y")) {
                if (enNumero) {
                    pendienteY = true;
                } else {
                    cerrar();
                }
                return;
            }
            if (MITAD.contains(token)) {
                aplicarMitad();
                return;
            }

            Long palabra = PALABRAS.get(token);
            if (palabra != null) {
                resolverY(BigDecimal.valueOf(palabra));
                sumar(BigDecimal.valueOf(palabra));
                return;
            }
            if (ESCALA_CIEN.contains(token)) {
                resolverY(null);
                aplicarEscala(CIEN);
                return;
            }
            if (ESCALA_MIL.contains(token)) {
                resolverY(null);
                aplicarEscala(MIL);
                return;
            }
            if (ESCALA_MILLON.contains(token)) {
                resolverY(null);
                aplicarEscala(MILLON);
                return;
            }

            BigDecimal digitos = parsearDigitos(token);
            if (digitos != null) {
                resolverY(digitos);
                sumar(digitos);
                return;
            }

            cerrar();
        }

        /**
         * Decide qué era la "y" pendiente, ahora que sabemos qué la sigue:
         * si continúa la misma decena sigue el número, si no arranca uno nuevo.
         */
        private void resolverY(BigDecimal siguiente) {
            if (!pendienteY) {
                return;
            }
            pendienteY = false;
            if (!continuaDecena(siguiente)) {
                cerrar();
            }
        }

        /**
         * "treinta y cinco" continúa; "cinco lucas y veinte mil" no. La decena
         * tiene que estar abierta (parcial termina en 0 sin ser centena redonda)
         * y no puede haberse aplicado todavía una escala a este bloque.
         */
        private boolean continuaDecena(BigDecimal siguiente) {
            if (siguiente == null || ultimaEscala.signum() != 0 || parcial.signum() == 0) {
                return false;
            }
            if (siguiente.compareTo(BigDecimal.ONE) < 0 || siguiente.compareTo(BigDecimal.TEN) >= 0) {
                return false;
            }
            long decenas = parcial.longValue();
            return decenas % 10 == 0 && decenas % 100 != 0;
        }

        private void sumar(BigDecimal valor) {
            parcial = parcial.add(valor);
            enNumero = true;
        }

        /** "mil" sin nada delante vale 1000, no 0. */
        private void aplicarEscala(BigDecimal escala) {
            BigDecimal base = parcial.signum() == 0 ? BigDecimal.ONE : parcial;
            total = total.add(base.multiply(escala));
            parcial = BigDecimal.ZERO;
            ultimaEscala = escala;
            enNumero = true;
        }

        /** "cinco lucas y media" = 5500. Sin escala previa, "media" no es un monto. */
        private void aplicarMitad() {
            pendienteY = false;
            if (!enNumero || ultimaEscala.signum() == 0) {
                cerrar();
                return;
            }
            total = total.add(ultimaEscala.divide(DOS));
        }

        private void cerrar() {
            if (enNumero) {
                BigDecimal valor = total.add(parcial);
                if (valor.signum() > 0) {
                    destino.add(valor);
                }
            }
            total = BigDecimal.ZERO;
            parcial = BigDecimal.ZERO;
            ultimaEscala = BigDecimal.ZERO;
            enNumero = false;
            pendienteY = false;
        }

        /**
         * El punto separa miles ("150.000") y la coma decimales ("5,50"), que es
         * como se escribe acá y como lo devuelve el modelo.
         */
        private BigDecimal parsearDigitos(String token) {
            if (!token.matches("\\d[\\d.,]*")) {
                return null;
            }
            String limpio = token.matches("\\d{1,3}(\\.\\d{3})+(,\\d+)?")
                    ? token.replace(".", "")
                    : token;
            try {
                return new BigDecimal(limpio.replace(",", "."));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
