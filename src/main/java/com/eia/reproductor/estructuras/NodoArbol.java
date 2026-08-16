package com.eia.reproductor.estructuras;

/** Nodo del {@link ArbolBinarioBusqueda}: dos hijos y, sobre todo, un puntero al padre. */
class NodoArbol<T> extends Nodo<T> {
    private NodoArbol<T> izquierdo;
    private NodoArbol<T> derecho;
    private NodoArbol<T> padre;

    /** Crea una hoja suelta, sin hijos ni padre. */
    NodoArbol(T dato) {
        super(dato);
    }

    /** @return el hijo izquierdo, o {@code null} si no tiene */
    NodoArbol<T> getIzquierdo() {
        return izquierdo;
    }

    void setIzquierdo(NodoArbol<T> izquierdo) {
        this.izquierdo = izquierdo;
    }

    /** @return el hijo derecho, o {@code null} si no tiene */
    NodoArbol<T> getDerecho() {
        return derecho;
    }

    void setDerecho(NodoArbol<T> derecho) {
        this.derecho = derecho;
    }

    /** @return el padre, o {@code null} si este nodo es la raiz */
    NodoArbol<T> getPadre() {
        return padre;
    }

    void setPadre(NodoArbol<T> padre) {
        this.padre = padre;
    }

    /** @return {@code true} si el nodo no tiene hijos */
    boolean esHoja() {
        return izquierdo == null && derecho == null;
    }
}
