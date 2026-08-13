package com.eia.reproductor.estructuras;

/**
 * Clase base de todos los nodos de las estructuras del proyecto.
 *
 * <p>Las tres estructuras enlazan sus elementos de forma distinta (un nodo de cola apunta solo
 * hacia adelante, uno de lista doble apunta en ambos sentidos y uno de arbol tiene dos hijos y un
 * padre), pero todas comparten una responsabilidad: guardar el dato. Esa parte comun vive aqui y
 * los nodos concretos la heredan, en lugar de repetir el mismo campo tres veces.</p>
 *
 * <p>La clase tiene visibilidad de paquete a proposito: los nodos son un detalle interno de las
 * estructuras. Ninguna clase de {@code modos}, {@code servicios} o {@code controlador} puede verlos
 * ni manipularlos, que es justo lo que se espera del encapsulamiento.</p>
 *
 * @param <T> tipo del dato almacenado
 */
abstract class Nodo<T> {

    private T dato;

    /**
     * Construye el nodo con su carga util.
     *
     * @param dato elemento que guarda el nodo
     */
    protected Nodo(T dato) {
        this.dato = dato;
    }

    /**
     * @return el dato almacenado
     */
    T getDato() {
        return dato;
    }

    /**
     * @param dato nuevo dato almacenado
     */
    void setDato(T dato) {
        this.dato = dato;
    }

    @Override
    public String toString() {
        return String.valueOf(dato);
    }
}
