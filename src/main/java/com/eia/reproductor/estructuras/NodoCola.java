package com.eia.reproductor.estructuras;

/** Nodo de la {@link ColaSimple}: solo conoce al que va detras de el. */
class NodoCola<T> extends Nodo<T> {
    private NodoCola<T> siguiente;

    /** Crea un nodo sin sucesor. */
    NodoCola(T dato) {
        super(dato);
        this.siguiente = null;
    }

    /** @return el siguiente nodo de la cola, o {@code null} si este es el ultimo */
    NodoCola<T> getSiguiente() {
        return siguiente;
    }

    void setSiguiente(NodoCola<T> siguiente) {
        this.siguiente = siguiente;
    }
}
