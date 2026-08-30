package com.pocket.config;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reglas de negocio y parámetros de integración, cargados desde application.yml.
 *
 * Tener los umbrales acá y no hardcodeados permite testear el dominio con
 * otros valores sin tocar una sola clase.
 */
@Getter @Setter
@ConfigurationProperties(prefix = "pocket")
public class PocketProperties {

    private Hormiga hormiga = new Hormiga();
    private Promedio promedio = new Promedio();
    private Resumen resumen = new Resumen();
    private Cuotas cuotas = new Cuotas();
    private Periodo periodo = new Periodo();
    private Moneda moneda = new Moneda();
    private Security security = new Security();
    private Ia ia = new Ia();
    private Cotizacion cotizacion = new Cotizacion();
    private Web web = new Web();
    private Demo demo = new Demo();

    /** Detección de gasto hormiga (RN-01, RN-02). */
    @Getter @Setter
    public static class Hormiga {
        private int umbralOcurrencias = 3;
        private boolean excluirCuotas = true;

        /**
         * Los gastos fijos tampoco cuentan como hormiga.
         *
         * Un gasto hormiga es un gasto chico, repetido y <b>evitable</b>: el
         * café de todos los días, el delivery del viernes. La luz, el internet
         * y el alquiler se repiten todos los meses por definición y no hay
         * ninguna acción que el usuario pueda tomar la próxima vez. Contarlos
         * pintaría "Servicios" en rojo todos los meses y el aviso dejaría de
         * significar algo.
         */
        private boolean excluirFijos = true;
    }

    /** Promedio histórico (RN-05). */
    @Getter @Setter
    public static class Promedio {
        private int mesesMinimos = 2;
        private int ventanaMeses = 12;
    }

    /** Preview de movimientos del resumen. */
    @Getter @Setter
    public static class Resumen {
        /** Cuántos movimientos devuelve el resumen. El listado completo es
         *  otra pantalla, servida por GET /api/gastos. */
        private int movimientosMaximos = 3;
    }

    /** Cuotas de crédito (RN-03, RN-04). */
    @Getter @Setter
    public static class Cuotas {
        private int maximo = 60;
        private int desfasePrimeraCuota = 1;
    }

    /** Ciclo mensual (RN-07, RN-08). */
    @Getter @Setter
    public static class Periodo {
        private String zonaHoraria = "America/Argentina/Buenos_Aires";
    }

    @Getter @Setter
    public static class Moneda {
        private String codigoBase = "ARS";
        private int refrescoCotizacionMinutos = 30;
    }

    @Getter @Setter
    public static class Security {
        private Jwt jwt = new Jwt();

        @Getter @Setter
        public static class Jwt {
            private String secret;
            private int expiracionDias = 365;
            private String emisor = "pocket-api";
        }
    }

    /** Integración con el proveedor de IA (RF-05). */
    @Getter @Setter
    public static class Ia {
        private String proveedor = "gemini";
        private String modelo;
        private String urlBase;
        private String apiKey;

        /** Cuánto esperar la respuesta del modelo. Generar el JSON es lo lento. */
        private int timeoutSegundos = 90;

        /**
         * Establecer la conexión es rápido o no va a serlo nunca. Separado del
         * timeout de lectura para no esperar 90s por un DNS que no resuelve.
         */
        private int conexionTimeoutSegundos = 10;

        /** Intentos totales, no reintentos: en 1 queda desactivado. */
        private int intentos = 3;

        /** Espera antes del segundo intento; se duplica en cada uno siguiente. */
        private long backoffInicialMs = 1000;

        /**
         * Techo de la espera entre intentos.
         *
         * Gemini indica cuánto esperar en el cuerpo del 429 (`RetryInfo.retryDelay`)
         * y ese valor manda por sobre el backoff exponencial: reintentar antes es
         * garantizarse otro 429. Pero el pedido puede ser de minutos —cuando la
         * cuota agotada es la diaria— y del otro lado hay un usuario con el
         * teléfono en la mano esperando. Si lo que pide supera este techo, no se
         * espera: se corta y se propaga el error.
         */
        private long esperaMaximaMs = 10_000;

        /**
         * Tope de procesamientos de audio por usuario y por día. 0 = sin tope.
         *
         * Existe por la demo pública: la cuota del free tier es de 20 requests
         * diarios <b>por modelo</b>, y sin un tope el primer visitante que se
         * entusiasme deja sin audio a todos los que entren después. También
         * evita que alguien queme la key a propósito, que es barato de hacer
         * cuando el alta de usuario es anónima.
         */
        private int limiteDiarioPorUsuario = 0;
    }

    /**
     * Exposición a navegadores.
     *
     * La app nativa no necesita CORS: el navegador es el único que aplica la
     * política del mismo origen. Pero la demo web del portfolio corre en otro
     * dominio que la API, así que sin esto el navegador bloquea cada request
     * antes de que salga.
     *
     * La lista vacía por defecto es deliberada: en local y en la app nativa no
     * hay ningún origen que habilitar, y una whitelist vacía no es lo mismo que
     * un "*" olvidado en producción.
     */
    @Getter @Setter
    public static class Web {
        private List<String> origenesPermitidos = List.of();
    }

    /**
     * Datos de ejemplo para la demo pública.
     *
     * La app es anónima por dispositivo, así que un visitante nuevo abre la
     * demo y ve todas las pantallas en cero: sin gráfico, sin hormigas, sin
     * promedio histórico. Con esto activado, cada usuario nuevo nace con unos
     * meses de historia y puede tocar y borrar todo sin afectar al siguiente.
     *
     * Apagado por defecto: en desarrollo y en un uso real, inventarle gastos a
     * alguien que recién instala la app sería exactamente lo que no se quiere.
     */
    @Getter @Setter
    public static class Demo {
        private boolean sembrar = false;

        /**
         * Meses de historia que se generan, contando el actual.
         *
         * Cuatro y no tres: el promedio histórico exige <b>más</b> de
         * `promedio.meses-minimos` meses previos, así que con tres el visitante
         * vería la comparación contra el promedio siempre vacía.
         */
        private int mesesHistoria = 4;
    }

    /** Integración con la API del dólar blue (RF-38, RF-40). */
    @Getter @Setter
    public static class Cotizacion {
        private String url;
        private int timeoutSegundos = 10;
    }
}
