package com.pocket.config;

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

    /** Detección de gasto hormiga (RN-01, RN-02). */
    @Getter @Setter
    public static class Hormiga {
        private int umbralOcurrencias = 3;
        private boolean excluirCuotas = true;
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
    }

    /** Integración con la API del dólar blue (RF-38, RF-40). */
    @Getter @Setter
    public static class Cotizacion {
        private String url;
        private int timeoutSegundos = 10;
    }
}
