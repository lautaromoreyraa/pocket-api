package com.pocket.service.ia;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pocket.config.PocketProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReintentosDeIA {

    private static final Pattern RETRY_DELAY =
            Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+(?:\\.\\d+)?)s\"");

    private final PocketProperties props;

    public <T> T ejecutar(Supplier<T> llamada) {
        int intentos = Math.max(1, props.getIa().getIntentos());
        long techo = props.getIa().getEsperaMaximaMs();
        long backoff = props.getIa().getBackoffInicialMs();

        for (int intento = 1; ; intento++) {
            try {
                return llamada.get();
            } catch (RestClientException e) {
                if (intento >= intentos || !esTransitorio(e)) {
                    throw e;
                }

                Long pedida = esperaPedida(e);
                long espera = pedida == null ? backoff : Math.max(pedida, backoff);

                if (espera > techo) {
                    log.warn("Intento {}/{} falló y el proveedor pide esperar {}ms, más que el "
                                    + "techo de {}ms. No se reintenta.",
                            intento, intentos, espera, techo);
                    throw e;
                }

                log.warn("Intento {}/{} falló ({}). Reintento en {}ms{}",
                        intento, intentos, e.getMessage(), espera,
                        pedida == null ? "" : " (pedido por el proveedor)");
                dormir(espera);
                backoff *= 2;
            }
        }
    }

    private Long esperaPedida(RestClientException e) {
        if (!(e instanceof HttpStatusCodeException http)) {
            return null;
        }

        String retryAfter = http.getResponseHeaders() == null
                ? null : http.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                return Long.parseLong(retryAfter.trim()) * 1000;
            } catch (NumberFormatException ignorado) {
            }
        }

        Matcher m = RETRY_DELAY.matcher(http.getResponseBodyAsString());
        if (!m.find()) {
            return null;
        }
        return Math.round(Double.parseDouble(m.group(1)) * 1000);
    }

    private boolean esTransitorio(RestClientException e) {
        if (e instanceof HttpServerErrorException) {
            return true;
        }
        if (e instanceof HttpClientErrorException clientError) {
            return clientError.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
        }
        return e instanceof ResourceAccessException;
    }

    private void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reintento interrumpido", e);
        }
    }
}
