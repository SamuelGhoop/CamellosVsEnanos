package com.eia.reproductor.estructuras;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Cola simple FIFO, implementada desde cero. */
public class ColaSimple<T> implements Iterable<T> {
    private NodoCola<T> frente;
    private NodoCola<T> fin;
    private int tamanio;

    /** Crea una cola vacia. */
    public ColaSimple() {
        this.frente = null;
        this.fin = null;
        this.tamanio = 0;
    }

    // --- Operaciones propias de la cola ---

    /** Agrega un elemento al final de la cola. O(1). */
    public void encolar(T dato) {
        Objects.requireNonNull(dato, "No se admiten elementos nulos en la cola.");
        NodoCola<T> nuevo = new NodoCola<>(dato);
        if (estaVacia()) {
            frente = nuevo;
        } else {
            fin.setSiguiente(nuevo);
        }
        fin = nuevo;
        tamanio++;
    }

    /** Retira y devuelve el elemento del frente. O(1). */
    public T desencolar() {
        exigirNoVacia();
        NodoCola<T> retirado = frente;
        frente = frente.getSiguiente();
        if (frente == null) {
            // La cola quedo vacia: hay que soltar tambien el fin, si no quedaria apuntando a un
            // nodo que ya no pertenece a la cola y el proximo encolar lo engancharia detras de el.
            fin = null;
        }
        retirado.setSiguiente(null);
        tamanio--;
        return retirado.getDato();
    }

    /** Consulta el elemento del frente sin retirarlo. O(1). */
    public T verFrente() {
        exigirNoVacia();
        return frente.getDato();
    }

    /** Consulta el ultimo elemento encolado sin retirarlo. O(1). */
    public T verFin() {
        exigirNoVacia();
        return fin.getDato();
    }

    // --- Consultas generales ---

    /** @return cantidad de elementos en la cola. O(1). */
    public int tamanio() {
        return tamanio;
    }

    /** @return {@code true} si la cola no tiene elementos. O(1). */
    public boolean estaVacia() {
        return tamanio == 0;
    }

    /** Indica si un elemento esta en la cola. O(n). */
    public boolean buscar(T dato) {
        if (dato == null) {
            return false;
        }
        for (NodoCola<T> recorrido = frente; recorrido != null; recorrido = recorrido.getSiguiente()) {
            if (dato.equals(recorrido.getDato())) {
                return true;
            }
        }
        return false;
    }

    /** Vacia la cola por completo. O(1). */
    public void limpiar() {
        frente = null;
        fin = null;
        tamanio = 0;
    }

    // --- Iterable ---

    /** Recorre la cola del frente hacia el fin sin desencolar. O(n). */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private NodoCola<T> siguienteNodo = frente;

            @Override
            public boolean hasNext() {
                return siguienteNodo != null;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("La cola ya se recorrio por completo.");
                }
                T dato = siguienteNodo.getDato();
                siguienteNodo = siguienteNodo.getSiguiente();
                return dato;
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder texto = new StringBuilder("frente -> [");
        boolean primerElemento = true;
        for (T dato : this) {
            if (!primerElemento) {
                texto.append(", ");
            }
            texto.append(dato);
            primerElemento = false;
        }
        return texto.append("] <- fin").toString();
    }

    private void exigirNoVacia() {
        if (estaVacia()) {
            throw new NoSuchElementException("La cola esta vacia.");
        }
    }
}
