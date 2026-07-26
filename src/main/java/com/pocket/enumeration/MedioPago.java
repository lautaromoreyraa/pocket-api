package com.pocket.enumeration;

public enum MedioPago {

    EFECTIVO,
    TRANSFERENCIA,
    DEBITO,
    CREDITO;

    /** Todo gasto con crédito va a la pestaña de crédito, tenga 1 cuota o 30 (RF-18). */
    public boolean esCredito() {
        return this == CREDITO;
    }

    public boolean seImputaAlMesSiguiente() {
        return esCredito();
    }
}
