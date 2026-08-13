package com.eia.reproductor.estructuras;

/**
 * Nodo del {@link ArbolBinarioBusqueda}: dos hijos y, sobre todo, un puntero al padre.
 *
 * <p><b>Por que el puntero al padre.</b> Un ABB clasico solo necesita {@code izquierdo} y
 * {@code derecho}. Aqui se agrega {@code padre} porque el modo alfabetico tiene que poder avanzar y
 * retroceder cancion por cancion sobre el arbol vivo, sin volcarlo a una lista. El sucesor inorden
 * de un nodo que no tiene subarbol derecho es un <i>ancestro</i>, y sin el enlace hacia arriba la
 * unica forma de encontrarlo seria recorrer el arbol desde la raiz otra vez.</p>
 *
 * @param <T> tipo del dato almacenado
 */
class NodoArbol<T> extends Nodo<T> {

    private NodoArbol<T> izquierdo;
    private NodoArbol<T> derecho;
    private NodoArbol<T> padre;

    /**
     * Crea una hoja suelta, sin hijos ni padre.
     *
     * @param dato elemento que guarda el nodo
     */
    NodoArbol(T dato) {
        super(dato);
    }

    /** @return el hijo izquierdo, o {@code null} si no tiene */
    NodoArbol<T> getIzquierdo() {
        return izquierdo;
    }

    /** @param izquierdo nuevo hijo izquierdo */
    void setIzquierdo(NodoArbol<T> izquierdo) {
        this.izquierdo = izquierdo;
    }

    /** @return el hijo derecho, o {@code null} si no tiene */
    NodoArbol<T> getDerecho() {
        return derecho;
    }

    /** @param derecho nuevo hijo derecho */
    void setDerecho(NodoArbol<T> derecho) {
        this.derecho = derecho;
    }

    /** @return el padre, o {@code null} si este nodo es la raiz */
    NodoArbol<T> getPadre() {
        return padre;
    }

    /** @param padre nuevo padre */
    void setPadre(NodoArbol<T> padre) {
        this.padre = padre;
    }

    /** @return {@code true} si el nodo no tiene hijos */
    boolean esHoja() {
        return izquierdo == null && derecho == null;
    }
}
