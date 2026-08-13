package com.eia.reproductor.estructuras;

/**
 * Nodo de la {@link ColaSimple}: solo conoce al que va detras de el.
 *
 * <p>Una cola FIFO nunca necesita mirar hacia atras, asi que este nodo tiene un unico enlace.
 * Es la diferencia estructural con {@link NodoDoble} y la razon por la que el modo de orden de
 * llegada no puede ofrecer el boton "Anterior".</p>
 *
 * @param <T> tipo del dato almacenado
 */
class NodoCola<T> extends Nodo<T> {

    private NodoCola<T> siguiente;

    /**
     * Crea un nodo sin sucesor.
     *
     * @param dato elemento que guarda el nodo
     */
    NodoCola(T dato) {
        super(dato);
        this.siguiente = null;
    }

    /** @return el siguiente nodo de la cola, o {@code null} si este es el ultimo */
    NodoCola<T> getSiguiente() {
        return siguiente;
    }

    /** @param siguiente nuevo nodo sucesor */
    void setSiguiente(NodoCola<T> siguiente) {
        this.siguiente = siguiente;
    }
}
