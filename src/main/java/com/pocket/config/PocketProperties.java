package com.pocket.config;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
    private Turnstile turnstile = new Turnstile();

    @Getter @Setter
    public static class Hormiga {
        private int umbralOcurrencias = 3;
        private boolean excluirCuotas = true;

        private boolean excluirFijos = true;
    }

    @Getter @Setter
    public static class Promedio {
        private int mesesMinimos = 2;
        private int ventanaMeses = 12;
    }

    @Getter @Setter
    public static class Resumen {
        private int movimientosMaximos = 3;
    }

    @Getter @Setter
    public static class Cuotas {
        private int maximo = 60;
        private int desfasePrimeraCuota = 1;
    }

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

    @Getter @Setter
    public static class Ia {
        private String proveedor = "gemini";
        private String modelo;
        private String urlBase;
        private String apiKey;

        private int timeoutSegundos = 90;

        private int conexionTimeoutSegundos = 10;

        private int intentos = 3;

        private long backoffInicialMs = 1000;

        private long esperaMaximaMs = 10_000;

        private int limiteDiarioPorUsuario = 0;

        private int limiteDiarioGlobal = 0;

        private Groq groq = new Groq();

        @Getter @Setter
        public static class Groq {
            private String urlBase = "https://api.groq.com/openai/v1";

            private String modeloVoz = "whisper-large-v3";

            private String modeloTexto = "openai/gpt-oss-20b";

            private String apiKey;
        }
    }

    @Getter @Setter
    public static class Web {
        private List<String> origenesPermitidos = List.of();
    }

    @Getter @Setter
    public static class Demo {
        private boolean sembrar = false;

        private int mesesHistoria = 4;
    }

    @Getter @Setter
    public static class Turnstile {
        private String secreto;
        private String url = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
        private int timeoutSegundos = 10;
    }

    @Getter @Setter
    public static class Cotizacion {
        private String url;
        private int timeoutSegundos = 10;
    }
}
