package com.pocket.service.ia;

import com.pocket.domain.Categoria;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Prompts de extracción de gastos (RF-05, RF-34), separados del service a
 * propósito: es lo que más se ajusta probando con audios reales, y no hace
 * falta abrir el service para tocarlo.
 *
 * Hay dos variantes porque hay dos formas de llegar al mismo JSON:
 *
 * <ul>
 *   <li>{@link #desdeAudio} — un modelo multimodal escucha el audio y devuelve
 *       los gastos <b>más</b> lo que dice haber escuchado;</li>
 *   <li>{@link #desdeTranscripcion} — el audio ya lo transcribió un modelo de
 *       reconocimiento de voz y acá solo se extraen los gastos de ese texto.</li>
 * </ul>
 *
 * La segunda es más segura contra gastos fabricados: el modelo que extrae no
 * escucha nada, así que no puede inventar un monto que no esté escrito.
 */
public final class PromptDeExtraccion {

    private PromptDeExtraccion() {}

    /** Para un modelo que recibe el audio y tiene que transcribirlo él mismo. */
    public static String desdeAudio(List<Categoria> categorias) {
        String listaCategorias = categorias.stream()
                .map(Categoria::getNombre)
                .collect(Collectors.joining(", "));

        return """
                Sos un asistente que escucha audios de personas argentinas
                contando en qué gastaron plata, en español rioplatense, y
                extrae cada gasto como un objeto JSON.

                Categorías válidas (usá EXACTAMENTE uno de estos nombres,
                elegí el que mejor calce; si ninguno aplica, usá "Otros"):
                %s

                Reglas para interpretar montos en jerga rioplatense:
                - "luca" o "lucas" = mil. "cinco lucas" = 5000.
                - "gamba" o "gambas" = cien. "dos gambas" = 200.
                - "palo" o "palos" = millón. "un palo" = 1000000.
                - Los números pueden venir en palabras ("veinte mil") o en
                  dígitos ("20000"): interpretalos igual.
                - "veinte mil de nafta" = monto 20000, categoría Combustible.

                Medio de pago (elegí uno de EFECTIVO, TRANSFERENCIA, DEBITO,
                CREDITO):
                - Si no se menciona ningún medio de pago, usá EFECTIVO.
                - "con crédito", "con la tarjeta de crédito", "en cuotas" -> CREDITO.
                - "con débito", "con la tarjeta de débito" -> DEBITO.
                - "transferencia", "transferí" -> TRANSFERENCIA.

                Cantidad de cuotas:
                - Si mencionan una cantidad de cuotas o pagos ("en 6 cuotas",
                  "en tres pagos", "financiado en 12"), poné ese número en
                  "cantidadCuotas".
                - Si no dicen nada de cuotas, "cantidadCuotas" va en null. No
                  la inventes ni asumas 1 solo porque el medio de pago sea
                  CREDITO: la ausencia de dato es distinta de "una cuota".

                Devolvé EXCLUSIVAMENTE un objeto JSON puro (sin texto extra,
                sin explicaciones y sin envolverlo en backticks de markdown),
                con esta forma exacta:
                {
                  "transcripcion": "<breve resumen en español de lo que se entendió>",
                  "gastos": [
                    {
                      "monto": <número, sin separador de miles, con punto decimal>,
                      "categoria": "<uno de los nombres de la lista de arriba>",
                      "descripcion": "<detalle breve, en pocas palabras>",
                      "medioPago": "<EFECTIVO | TRANSFERENCIA | DEBITO | CREDITO>",
                      "cantidadCuotas": <número entero, o null si no se mencionó>
                    }
                  ]
                }

                Si el audio no describe ningún gasto reconocible, "gastos"
                debe ser un array vacío: [].
                """.formatted(listaCategorias);
    }

    /**
     * Para un modelo de texto que recibe una transcripción ya hecha.
     *
     * La transcripción se le devuelve al cliente tal cual la entregó el modelo
     * de voz, sin que este segundo modelo pueda tocarla: es la evidencia contra
     * la que después se verifica cada monto, y no tendría sentido dejar que la
     * reescriba justo el que podría estar inventando.
     */
    public static String desdeTranscripcion(List<Categoria> categorias, String transcripcion) {
        String listaCategorias = categorias.stream()
                .map(Categoria::getNombre)
                .collect(Collectors.joining(", "));

        return """
                Sos un asistente que lee lo que una persona argentina dijo sobre
                en qué gastó plata, en español rioplatense, y extrae cada gasto
                como un objeto JSON.

                Categorías válidas (usá EXACTAMENTE uno de estos nombres,
                elegí el que mejor calce; si ninguno aplica, usá "Otros"):
                %s

                Reglas para interpretar montos en jerga rioplatense:
                - "luca" o "lucas" = mil. "cinco lucas" = 5000.
                - "gamba" o "gambas" = cien. "dos gambas" = 200.
                - "palo" o "palos" = millón. "un palo" = 1000000.
                - Los números pueden venir en palabras ("veinte mil") o en
                  dígitos ("20000"): interpretalos igual.
                - "veinte mil de nafta" = monto 20000, categoría Combustible.

                Medio de pago (elegí uno de EFECTIVO, TRANSFERENCIA, DEBITO,
                CREDITO):
                - Si no se menciona ningún medio de pago, usá EFECTIVO.
                - "con crédito", "con la tarjeta de crédito", "en cuotas" -> CREDITO.
                - "con débito", "con la tarjeta de débito" -> DEBITO.
                - "transferencia", "transferí" -> TRANSFERENCIA.

                Cantidad de cuotas:
                - Si mencionan una cantidad de cuotas o pagos ("en 6 cuotas",
                  "en tres pagos", "financiado en 12"), poné ese número en
                  "cantidadCuotas".
                - Si no dicen nada de cuotas, "cantidadCuotas" va en null. No
                  la inventes ni asumas 1 solo porque el medio de pago sea
                  CREDITO: la ausencia de dato es distinta de "una cuota".

                IMPORTANTE: no inventes montos. Extraé unicamente los que
                aparecen en el texto. Si el texto no describe ningún gasto
                reconocible, "gastos" debe ser un array vacío: [].

                Texto a analizar:
                ---
                %s
                ---

                Devolvé EXCLUSIVAMENTE un objeto JSON puro (sin texto extra,
                sin explicaciones y sin envolverlo en backticks de markdown),
                con esta forma exacta:
                {
                  "gastos": [
                    {
                      "monto": <número, sin separador de miles, con punto decimal>,
                      "categoria": "<uno de los nombres de la lista de arriba>",
                      "descripcion": "<detalle breve, en pocas palabras>",
                      "medioPago": "<EFECTIVO | TRANSFERENCIA | DEBITO | CREDITO>",
                      "cantidadCuotas": <número entero, o null si no se mencionó>
                    }
                  ]
                }
                """.formatted(listaCategorias, transcripcion);
    }
}
