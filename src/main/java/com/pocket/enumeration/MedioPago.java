package com.pocket.enumeration;

public enum MedioPago {

    EFECTIVO,
    TRANSFERENCIA,
    DEBITO,
    CREDITO;

    public boolean esCredito() {
        return this == CREDITO;
    }

    public boolean seImputaAlMesSiguiente() {
        return esCredito();
    }
}
