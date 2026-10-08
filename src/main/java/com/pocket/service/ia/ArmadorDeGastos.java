package com.pocket.service.ia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.dto.audio.AudioResponse;
import com.pocket.dto.audio.GastoDetectado;
import com.pocket.dto.ia.RespuestaModeloIA;
import com.pocket.enumeration.MedioPago;
import com.pocket.exception.AudioNoComprendidoException;
import com.pocket.util.MontosEnTranscripcion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Convierte lo que devolvió un modelo en gastos verificados.
 *
 * Vive fuera de las implementaciones de {@link ProcesadorIAService} porque nada
 * de lo que hace depende del proveedor: resolver la categoría contra la base,
 * elegir el medio de pago y —sobre todo— <b>descartar la respuesta si algún
 * monto no aparece en la transcripción</b> valen igual venga de donde venga.
 *
 * Que el control de coherencia esté acá y no duplicado en cada proveedor no es
 * cosmético: es la única defensa contra gastos fabricados, y dos copias son dos
 * lugares donde puede quedar desactualizada.
 *
 * No tiene interfaz propia a propósito. No es un caso de uso sino un
 * colaborador de los procesadores, igual que {@link PromptDeExtraccion}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArmadorDeGastos {

    private final PocketProperties props;

    /**
     * @param modelo        lo que devolvió el modelo, ya parseado
     * @param transcripcion contra la que se verifica cada monto
     * @param categorias    las de la base, para resolver los nombres del modelo
     * @throws AudioNoComprendidoException si no hay gastos o si alguno no cierra
     */
    public AudioResponse armar(RespuestaModeloIA modelo,
                               String transcripcion,
                               List<Categoria> categorias) {

        Map<String, Categoria> porNombre = categorias.stream()
                .collect(Collectors.toMap(c -> normalizar(c.getNombre()), c -> c, (a, b) -> a));
        Categoria otros = porNombre.get(normalizar(Categoria.OTROS));

        LocalDate hoy = LocalDate.now(ZoneId.of(props.getPeriodo().getZonaHoraria()));

        List<GastoDetectado> gastos = modelo.gastos() == null ? List.of() : modelo.gastos().stream()
                .filter(g -> g.monto() != null && g.monto().compareTo(BigDecimal.ZERO) > 0)
                .map(g -> aGastoDetectado(g, porNombre, otros, hoy))
                .toList();

        if (gastos.isEmpty()) {
            throw new AudioNoComprendidoException(
                    "No se detectó ningún gasto en el audio. Podés reintentar o cargarlo a mano.");
        }

        verificarCoherencia(gastos, transcripcion);

        return new AudioResponse(gastos, transcripcion);
    }

    /**
     * Todo monto detectado tiene que aparecer en la transcripción, en dígitos o
     * en letras. Si no aparece, el modelo lo fabricó.
     *
     * No es paranoia: un modelo que no puede decodificar el audio igual devuelve
     * un JSON bien formado, con montos y categorías verosímiles, en vez de
     * fallar. Sin este control eso llega al usuario como un 200 indistinguible
     * de una detección real.
     *
     * El criterio es deliberadamente estricto: alcanza con que un solo monto no
     * cierre para descartar la respuesta entera. Un 422 de más solo cuesta un
     * reintento o una carga manual; un gasto inventado con un monto plausible no
     * hay forma de que el usuario lo detecte.
     */
    private void verificarCoherencia(List<GastoDetectado> gastos, String transcripcion) {
        List<BigDecimal> fabricados = gastos.stream()
                .map(GastoDetectado::monto)
                .filter(monto -> !MontosEnTranscripcion.menciona(transcripcion, monto))
                .toList();

        if (!fabricados.isEmpty()) {
            log.warn("Respuesta descartada por incoherente: los montos {} no aparecen en la "
                            + "transcripción '{}'. Modelo: {}",
                    fabricados, transcripcion, props.getIa().getModelo());
            throw new AudioNoComprendidoException(
                    "No se pudo verificar lo que dice el audio. Podés reintentar o cargarlo a mano.");
        }
    }

    private GastoDetectado aGastoDetectado(RespuestaModeloIA.GastoModelo detectado,
                                           Map<String, Categoria> categoriasPorNombre,
                                           Categoria otros, LocalDate hoy) {
        Categoria categoria = detectado.categoria() == null
                ? null : categoriasPorNombre.get(normalizar(detectado.categoria()));
        if (categoria == null) {
            categoria = otros;
        }

        return new GastoDetectado(
                UUID.randomUUID(),
                detectado.monto(),
                categoria.getId(),
                detectado.descripcion(),
                resolverMedioPago(detectado.medioPago()),
                hoy,
                detectado.cantidadCuotas());
    }

    /** Medio de pago no reconocido o ausente -> EFECTIVO, el más común. */
    private MedioPago resolverMedioPago(String texto) {
        if (texto == null) {
            return MedioPago.EFECTIVO;
        }
        try {
            return MedioPago.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MedioPago.EFECTIVO;
        }
    }

    private String normalizar(String nombre) {
        return nombre.trim().toLowerCase(Locale.ROOT);
    }
}
