package com.pocket.service.demo.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.pocket.config.PocketProperties;
import com.pocket.domain.Categoria;
import com.pocket.domain.CompraFinanciada;
import com.pocket.domain.Gasto;
import com.pocket.domain.GastoFijo;
import com.pocket.domain.Ingreso;
import com.pocket.domain.Usuario;
import com.pocket.enumeration.MedioPago;
import com.pocket.enumeration.OrigenGasto;
import com.pocket.event.UsuarioCreado;
import com.pocket.repository.CategoriaRepository;
import com.pocket.repository.CompraFinanciadaRepository;
import com.pocket.repository.GastoFijoRepository;
import com.pocket.repository.GastoRepository;
import com.pocket.repository.IngresoRepository;
import com.pocket.repository.UsuarioRepository;
import com.pocket.service.cuota.CalculadoraCuotasService;
import com.pocket.service.demo.SembradorDemo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Arma la historia de ejemplo de un visitante de la demo.
 *
 * Los datos no son aleatorios sin más: están elegidos para que cada pantalla
 * tenga algo que mostrar. Concretamente, la siembra garantiza que
 *
 * <ul>
 *   <li>haya una categoría con 3 o más ocurrencias en el mes en curso, para que
 *       la detección de hormigas (RN-01) tenga algo que detectar;</li>
 *   <li>los gastos con crédito estén cargados en el <b>mes anterior</b>, porque
 *       se imputan al siguiente (RN-03) y si no la pestaña de crédito del mes
 *       actual aparecería vacía;</li>
 *   <li>exista una compra en cuotas empezada meses atrás, para que se vean
 *       cuotas en curso y compromisos futuros;</li>
 *   <li>haya varios meses con movimientos, que es lo que el promedio histórico
 *       necesita para poder comparar (RN-05).</li>
 * </ul>
 *
 * La semilla del generador sale del id del usuario: cada visitante ve números
 * distintos, pero los suyos son siempre los mismos si vuelve a entrar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SembradorDemoImpl implements SembradorDemo {

    private final PocketProperties props;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final GastoRepository gastoRepository;
    private final IngresoRepository ingresoRepository;
    private final GastoFijoRepository gastoFijoRepository;
    private final CompraFinanciadaRepository compraRepository;
    private final CalculadoraCuotasService calculadoraCuotas;

    /**
     * Corre después del commit del alta y en una transacción propia.
     *
     * Las dos cosas importan: después, porque el usuario tiene que existir de
     * verdad antes de que algo le apunte con una FK; y aparte, porque así un
     * fallo de la siembra se revierte solo a sí mismo. La identificación ya
     * ocurrió y el token ya es válido: el visitante entra igual, con la cuenta
     * vacía, en vez de quedarse afuera por un dato de adorno.
     */
    @Override
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sembrarSiCorresponde(UsuarioCreado evento) {
        if (!props.getDemo().isSembrar()) {
            return;
        }

        try {
            usuarioRepository.findById(evento.usuarioId()).ifPresent(this::sembrar);
        } catch (RuntimeException e) {
            log.error("No se pudo sembrar la demo para el usuario {}", evento.usuarioId(), e);
        }
    }

    private void sembrar(Usuario usuario) {
        Map<String, Categoria> categorias = categoriaRepository.findAll().stream()
                .collect(Collectors.toMap(Categoria::getNombre, Function.identity()));

        ZoneId zona = ZoneId.of(props.getPeriodo().getZonaHoraria());
        YearMonth mesActual = YearMonth.now(zona);
        LocalDate hoy = LocalDate.now(zona);
        Random random = new Random(usuario.getId().getMostSignificantBits());

        int meses = Math.max(1, props.getDemo().getMesesHistoria());
        List<Gasto> gastos = new ArrayList<>();

        for (int atras = meses - 1; atras >= 0; atras--) {
            YearMonth mes = mesActual.minusMonths(atras);
            boolean esMesActual = atras == 0;

            sembrarIngreso(usuario, mes, random);
            gastos.addAll(gastosDelMes(usuario, categorias, mes, hoy, esMesActual, random));
        }

        gastoRepository.saveAll(gastos);
        sembrarCompraEnCuotas(usuario, categorias, mesActual, random);
        sembrarFijos(usuario, categorias, mesActual, hoy);

        log.info("Demo sembrada para el usuario {}: {} gastos en {} meses",
                usuario.getId(), gastos.size(), meses);
    }

    private void sembrarIngreso(Usuario usuario, YearMonth mes, Random random) {
        // El sueldo varía mes a mes, así el promedio histórico no queda idéntico
        // al mes en curso y la comparación dice algo.
        BigDecimal sueldo = BigDecimal.valueOf(1_450_000 + random.nextInt(120_000));

        ingresoRepository.save(Ingreso.builder()
                .usuario(usuario)
                .monto(sueldo)
                .descripcion("Sueldo")
                .periodo(mes.atDay(1))
                .build());
    }

    /**
     * Los gastos del mes. En el mes en curso se cargan cuatro deliverys a
     * propósito: son los que superan el umbral de hormiga y hacen que el aviso
     * y el badge de repeticiones tengan algo que mostrar.
     */
    private List<Gasto> gastosDelMes(Usuario usuario,
                                     Map<String, Categoria> categorias,
                                     YearMonth mes,
                                     LocalDate hoy,
                                     boolean esMesActual,
                                     Random random) {

        List<Gasto> delMes = new ArrayList<>();
        int ocurrenciasDelivery = esMesActual ? 4 : 2;

        for (int i = 0; i < ocurrenciasDelivery; i++) {
            delMes.add(gasto(usuario, categorias.get("Delivery/Restaurantes"),
                    BigDecimal.valueOf(14_000 + random.nextInt(9_000)),
                    "Pedido del finde", MedioPago.DEBITO,
                    diaDelMes(mes, 4 + i * 6, hoy)));
        }

        delMes.add(gasto(usuario, categorias.get("Supermercado"),
                BigDecimal.valueOf(78_000 + random.nextInt(25_000)),
                "Compra grande", MedioPago.DEBITO, diaDelMes(mes, 3, hoy)));
        delMes.add(gasto(usuario, categorias.get("Supermercado"),
                BigDecimal.valueOf(21_000 + random.nextInt(9_000)),
                "Lo que faltaba", MedioPago.EFECTIVO, diaDelMes(mes, 17, hoy)));

        for (int i = 0; i < 3; i++) {
            delMes.add(gasto(usuario, categorias.get("Transporte"),
                    BigDecimal.valueOf(2_400 + random.nextInt(1_200)),
                    "SUBE", MedioPago.DEBITO, diaDelMes(mes, 2 + i * 9, hoy)));
        }

        delMes.add(gasto(usuario, categorias.get("Combustible"),
                BigDecimal.valueOf(38_000 + random.nextInt(12_000)),
                "Nafta", MedioPago.DEBITO, diaDelMes(mes, 11, hoy)));
        delMes.add(gasto(usuario, categorias.get("Salud/Farmacia"),
                BigDecimal.valueOf(16_000 + random.nextInt(8_000)),
                "Farmacia", MedioPago.DEBITO, diaDelMes(mes, 14, hoy)));

        // Crédito: se imputa al mes siguiente (RN-03), así que para que la
        // pestaña de crédito del mes en curso tenga contenido, el gasto tiene
        // que estar fechado en un mes anterior. Cargarlo en el mes actual lo
        // mandaría al futuro y la pestaña se vería vacía.
        if (!esMesActual) {
            delMes.add(gasto(usuario, categorias.get("Ropa/Indumentaria"),
                    BigDecimal.valueOf(95_000 + random.nextInt(40_000)),
                    "Zapatillas", MedioPago.CREDITO, diaDelMes(mes, 20, hoy)));
            delMes.add(gasto(usuario, categorias.get("Entretenimiento"),
                    BigDecimal.valueOf(32_000 + random.nextInt(15_000)),
                    "Recital", MedioPago.CREDITO, diaDelMes(mes, 24, hoy)));
        }

        return delMes;
    }

    /**
     * Compra empezada dos meses atrás en seis cuotas: con el desfase de la
     * primera cuota, en el mes en curso se ve una cuota intermedia y quedan
     * varias por delante, que es lo que hace visible el compromiso futuro.
     */
    private void sembrarCompraEnCuotas(Usuario usuario,
                                       Map<String, Categoria> categorias,
                                       YearMonth mesActual,
                                       Random random) {

        CompraFinanciada compra = CompraFinanciada.builder()
                .usuario(usuario)
                .categoria(categorias.get("Hogar"))
                .idempotencyKey(UUID.randomUUID())
                .descripcion("Heladera")
                .montoTotal(BigDecimal.valueOf(890_000 + random.nextInt(200_000)))
                .cantidadCuotas(6)
                .fechaCompra(mesActual.minusMonths(2).atDay(15))
                .build();

        // Las cuotas las arma la calculadora: el redondeo de RN-04 vive ahí y
        // reimplementarlo acá sería tener dos verdades sobre el mismo número.
        compra.getCuotas().addAll(calculadoraCuotas.generarCuotas(compra));
        compraRepository.save(compra);
    }

    /**
     * Tres plantillas de gasto fijo, con el alquiler ya pagado este mes.
     *
     * Que una esté tildada y las otras no es deliberado: la pantalla de fijos
     * se entiende cuando se ven los dos estados juntos, y el total estimado
     * solo se distingue del registrado si hay algo de cada lado.
     */
    private void sembrarFijos(Usuario usuario,
                              Map<String, Categoria> categorias,
                              YearMonth mesActual,
                              LocalDate hoy) {

        GastoFijo alquiler = gastoFijoRepository.save(plantilla(
                usuario, categorias.get("Hogar"), "Alquiler",
                BigDecimal.valueOf(520_000), MedioPago.TRANSFERENCIA, 5));

        gastoFijoRepository.save(plantilla(
                usuario, categorias.get("Servicios"), "Luz",
                BigDecimal.valueOf(48_000), MedioPago.DEBITO, 12));

        gastoFijoRepository.save(plantilla(
                usuario, categorias.get("Suscripciones"), "Netflix",
                BigDecimal.valueOf(11_500), MedioPago.CREDITO, 20));

        // El tilde ES el gasto: no hay flag "pagado" que mantener aparte. La
        // fecha del gasto no puede ser futura, de ahí el mínimo contra hoy.
        LocalDate fechaPago = minimo(mesActual.atDay(alquiler.getDiaDelMes()), hoy);

        gastoRepository.save(Gasto.builder()
                .usuario(usuario)
                .categoria(alquiler.getCategoria())
                .gastoFijo(alquiler)
                .idempotencyKey(UUID.randomUUID())
                .monto(alquiler.getMonto())
                .descripcion(alquiler.getDescripcion())
                .medioPago(alquiler.getMedioPago())
                .origen(OrigenGasto.FIJO)
                .fechaGasto(fechaPago)
                .fechaImputacion(mesActual.atDay(1))
                .build());
    }

    private GastoFijo plantilla(Usuario usuario, Categoria categoria, String descripcion,
                                BigDecimal monto, MedioPago medioPago, int diaDelMes) {
        return GastoFijo.builder()
                .usuario(usuario)
                .categoria(categoria)
                .descripcion(descripcion)
                .monto(monto)
                .medioPago(medioPago)
                .diaDelMes(diaDelMes)
                .activo(true)
                .build();
    }

    private Gasto gasto(Usuario usuario, Categoria categoria, BigDecimal monto,
                        String descripcion, MedioPago medioPago, LocalDate fecha) {

        YearMonth imputacion = medioPago.seImputaAlMesSiguiente()
                ? YearMonth.from(fecha).plusMonths(1)
                : YearMonth.from(fecha);

        return Gasto.builder()
                .usuario(usuario)
                .categoria(categoria)
                .idempotencyKey(UUID.randomUUID())
                .monto(monto)
                .descripcion(descripcion)
                .medioPago(medioPago)
                .origen(OrigenGasto.MANUAL)
                .fechaGasto(fecha)
                .fechaImputacion(imputacion.atDay(1))
                .build();
    }

    /** Ningún gasto puede tener fecha futura, ni siquiera uno inventado. */
    private LocalDate diaDelMes(YearMonth mes, int dia, LocalDate hoy) {
        return minimo(mes.atDay(Math.min(dia, mes.lengthOfMonth())), hoy);
    }

    private LocalDate minimo(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? b : a;
    }
}
