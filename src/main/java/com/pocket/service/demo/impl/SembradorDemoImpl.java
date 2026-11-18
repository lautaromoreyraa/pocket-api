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
        BigDecimal sueldo = BigDecimal.valueOf(1_450_000 + random.nextInt(120_000));

        ingresoRepository.save(Ingreso.builder()
                .usuario(usuario)
                .monto(sueldo)
                .descripcion("Sueldo")
                .periodo(mes.atDay(1))
                .build());
    }

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

        compra.getCuotas().addAll(calculadoraCuotas.generarCuotas(compra));
        compraRepository.save(compra);
    }

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

    private LocalDate diaDelMes(YearMonth mes, int dia, LocalDate hoy) {
        return minimo(mes.atDay(Math.min(dia, mes.lengthOfMonth())), hoy);
    }

    private LocalDate minimo(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? b : a;
    }
}
