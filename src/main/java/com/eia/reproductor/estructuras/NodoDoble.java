package com.eia.reproductor.estructuras;

/** Nodo de la {@link ListaCircularDoble}: conoce a su vecino anterior y al siguiente. */
class NodoDoble<T> extends Nodo<T> {
    private NodoDoble<T> anterior;
    private NodoDoble<T> siguiente;

    /** Crea un nodo suelto y lo cierra sobre si mismo. */
    NodoDoble(T dato) {
        super(dato);
        this.anterior = this;
        this.siguiente = this;
    }

    /** @return el nodo que precede a este, nunca {@code null} mientras el nodo este en una lista */
    NodoDoble<T> getAnterior() {
        return anterior;
    }

    void setAnterior(NodoDoble<T> anterior) {
        this.anterior = anterior;
    }

    /** @return el nodo que sigue a este, nunca {@code null} mientras el nodo este en una lista */
    NodoDoble<T> getSiguiente() {
        return siguiente;
    }

    void setSiguiente(NodoDoble<T> siguiente) {
        this.siguiente = siguiente;
    }
}
