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
        private int timeoutSegundos = 30;
    }

    /** Integración con la API del dólar blue (RF-38, RF-40). */
    @Getter @Setter
    public static class Cotizacion {
        private String url;
        private int timeoutSegundos = 10;
    }
}
