package com.eia.reproductor.estructuras;

/** Clase base de todos los nodos de las estructuras del proyecto. */
abstract class Nodo<T> {
    private T dato;

    /** Construye el nodo con su carga util. */
    protected Nodo(T dato) {
        this.dato = dato;
    }

    /** @return el dato almacenado */
    T getDato() {
        return dato;
    }

    void setDato(T dato) {
        this.dato = dato;
    }

    @Override
    public String toString() {
        return String.valueOf(dato);
    }
}
