package com.eia.reproductor.estructuras;

/**
 * Nodo de la {@link ListaCircularDoble}: conoce a su vecino anterior y al siguiente.
 *
 * <p>En una lista circular doble bien construida <b>ninguno de los dos enlaces es nunca
 * {@code null}</b>. Si la lista tiene un solo elemento, el nodo se apunta a si mismo en ambas
 * direcciones. Esa invariante es la que garantiza que la reproduccion nunca llegue a un final.</p>
 *
 * @param <T> tipo del dato almacenado
 */
class NodoDoble<T> extends Nodo<T> {

    private NodoDoble<T> anterior;
    private NodoDoble<T> siguiente;

    /**
     * Crea un nodo suelto y lo cierra sobre si mismo.
     *
     * @param dato elemento que guarda el nodo
     */
    NodoDoble(T dato) {
        super(dato);
        this.anterior = this;
        this.siguiente = this;
    }

    /** @return el nodo que precede a este, nunca {@code null} mientras el nodo este en una lista */
    NodoDoble<T> getAnterior() {
        return anterior;
    }

    /** @param anterior nuevo nodo precedente */
    void setAnterior(NodoDoble<T> anterior) {
        this.anterior = anterior;
    }

    /** @return el nodo que sigue a este, nunca {@code null} mientras el nodo este en una lista */
    NodoDoble<T> getSiguiente() {
        return siguiente;
    }

    /** @param siguiente nuevo nodo sucesor */
    void setSiguiente(NodoDoble<T> siguiente) {
        this.siguiente = siguiente;
    }
}
