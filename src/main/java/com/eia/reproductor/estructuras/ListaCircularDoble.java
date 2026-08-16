package com.eia.reproductor.estructuras;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;

/** Lista ligada circular doble, implementada desde cero. */
public class ListaCircularDoble<T> implements Iterable<T> {
    private NodoDoble<T> primero;
    private int tamanio;

    /** Crea una lista vacia. */
    public ListaCircularDoble() {
        this.primero = null;
        this.tamanio = 0;
    }

    // --- Consultas basicas ---

    /** @return cantidad de elementos de la lista. O(1). */
    public int tamanio() {
        return tamanio;
    }

    /** @return {@code true} si la lista no tiene elementos. O(1). */
    public boolean estaVacia() {
        return tamanio == 0;
    }

    // --- Altas ---

    /** Agrega un elemento al final de la lista. O(1). */
    public void agregar(T dato) {
        Objects.requireNonNull(dato, "No se admiten elementos nulos en la lista.");
        NodoDoble<T> nuevo = new NodoDoble<>(dato);
        if (estaVacia()) {
            // El nodo ya nace apuntandose a si mismo, solo falta declararlo primero.
            primero = nuevo;
        } else {
            NodoDoble<T> ultimo = primero.getAnterior();
            enlazar(ultimo, nuevo);
            enlazar(nuevo, primero);
        }
        tamanio++;
    }

    /** Inserta un elemento en una posicion concreta. O(n). */
    public void agregarEnPosicion(int posicion, T dato) {
        Objects.requireNonNull(dato, "No se admiten elementos nulos en la lista.");
        if (posicion < 0 || posicion > tamanio) {
            throw new IndexOutOfBoundsException(
                    "Posicion " + posicion + " fuera de rango [0, " + tamanio + "].");
        }
        if (posicion == tamanio) {
            agregar(dato);
            return;
        }
        NodoDoble<T> referencia = nodoEn(posicion);
        NodoDoble<T> nuevo = new NodoDoble<>(dato);
        enlazar(referencia.getAnterior(), nuevo);
        enlazar(nuevo, referencia);
        if (posicion == 0) {
            primero = nuevo;
        }
        tamanio++;
    }

    // --- Bajas ---

    /** Elimina la primera aparicion de un elemento. O(n). */
    public boolean eliminar(T dato) {
        NodoDoble<T> objetivo = nodoDe(dato);
        if (objetivo == null) {
            return false;
        }
        desenlazar(objetivo);
        return true;
    }

    /** Deja la lista vacia. O(1). */
    public void limpiar() {
        primero = null;
        tamanio = 0;
    }

    // --- Consultas ---

    /** Indica si un elemento esta en la lista. O(n). */
    public boolean buscar(T dato) {
        return nodoDe(dato) != null;
    }

    /** Devuelve el elemento que esta en una posicion. O(n). */
    public T obtener(int posicion) {
        return nodoEn(posicion).getDato();
    }

    /** Devuelve el primer elemento de la lista. O(1). */
    public T primero() {
        exigirNoVacia();
        return primero.getDato();
    }

    /** Devuelve el ultimo elemento de la lista. O(1). */
    public T ultimo() {
        exigirNoVacia();
        return primero.getAnterior().getDato();
    }

    // --- Mezcla ---

    /** Baraja la lista con el algoritmo de Fisher-Yates. O(n). */
    public void mezclar() {
        mezclar(new Random());
    }

    /** Baraja la lista usando una fuente de aleatoriedad concreta. O(n). */
    public void mezclar(Random aleatorio) {
        Objects.requireNonNull(aleatorio, "El generador aleatorio no puede ser nulo.");
        if (tamanio < 2) {
            return;
        }

        @SuppressWarnings("unchecked")
        NodoDoble<T>[] nodos = new NodoDoble[tamanio];
        NodoDoble<T> recorrido = primero;
        for (int i = 0; i < tamanio; i++) {
            nodos[i] = recorrido;
            recorrido = recorrido.getSiguiente();
        }

        // Fisher-Yates: se avanza de atras hacia adelante intercambiando cada posicion con una
        // elegida al azar entre las que quedan. Garantiza que las n! permutaciones sean
        for (int i = nodos.length - 1; i > 0; i--) {
            int j = aleatorio.nextInt(i + 1);
            NodoDoble<T> temporal = nodos[i];
            nodos[i] = nodos[j];
            nodos[j] = temporal;
        }

        for (int i = 0; i < nodos.length; i++) {
            enlazar(nodos[i], nodos[(i + 1) % nodos.length]);
        }
        primero = nodos[0];
    }

    // --- Cursor ---

    /** Crea un cursor posicionado en el primer elemento. O(1). */
    public Cursor<T> nuevoCursor() {
        exigirNoVacia();
        return new Cursor<>(this, primero);
    }

    /** Puntero navegable sobre una {@link ListaCircularDoble}. */
    public static final class Cursor<T> {
        private final ListaCircularDoble<T> lista;
        private NodoDoble<T> actual;

        private Cursor(ListaCircularDoble<T> lista, NodoDoble<T> actual) {
            this.lista = lista;
            this.actual = actual;
        }

        /** @return el elemento sobre el que esta parado el cursor. O(1). */
        public T actual() {
            return actual.getDato();
        }

        /** Avanza una posicion y devuelve el elemento al que llego. O(1). */
        public T siguiente() {
            actual = actual.getSiguiente();
            return actual.getDato();
        }

        /** Retrocede una posicion y devuelve el elemento al que llego. O(1). */
        public T anterior() {
            actual = actual.getAnterior();
            return actual.getDato();
        }

        /** Mueve el cursor al nodo que contiene un elemento dado. O(n). */
        public boolean posicionarEn(T dato) {
            NodoDoble<T> objetivo = lista.nodoDe(dato);
            if (objetivo == null) {
                return false;
            }
            actual = objetivo;
            return true;
        }

        /** Devuelve el cursor al primer elemento de la lista. O(1). */
        public void reiniciar() {
            lista.exigirNoVacia();
            actual = lista.primero;
        }
    }

    // --- Iterable ---

    /** Recorre la lista una sola vuelta, del primero al ultimo. O(n). */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private NodoDoble<T> siguienteNodo = primero;
            private int visitados = 0;

            @Override
            public boolean hasNext() {
                return visitados < tamanio;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("La lista ya se recorrio por completo.");
                }
                T dato = siguienteNodo.getDato();
                siguienteNodo = siguienteNodo.getSiguiente();
                visitados++;
                return dato;
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder texto = new StringBuilder("[");
        boolean primerElemento = true;
        for (T dato : this) {
            if (!primerElemento) {
                texto.append(", ");
            }
            texto.append(dato);
            primerElemento = false;
        }
        return texto.append("]").toString();
    }

    // --- Apoyo interno ---

    /** Conecta dos nodos: {@code izquierda} pasa a preceder a {@code derecha}. */
    private void enlazar(NodoDoble<T> izquierda, NodoDoble<T> derecha) {
        izquierda.setSiguiente(derecha);
        derecha.setAnterior(izquierda);
    }

    /** Saca un nodo del anillo y cierra el hueco. */
    private void desenlazar(NodoDoble<T> objetivo) {
        if (tamanio == 1) {
            limpiar();
            return;
        }
        enlazar(objetivo.getAnterior(), objetivo.getSiguiente());
        if (objetivo == primero) {
            primero = objetivo.getSiguiente();
        }
        // Se aislan los enlaces del nodo retirado para que no mantenga viva la lista.
        objetivo.setSiguiente(objetivo);
        objetivo.setAnterior(objetivo);
        tamanio--;
    }

    /** Busca el nodo que contiene un dato, o {@code null} si no esta. */
    private NodoDoble<T> nodoDe(T dato) {
        if (estaVacia() || dato == null) {
            return null;
        }
        NodoDoble<T> recorrido = primero;
        for (int i = 0; i < tamanio; i++) {
            if (dato.equals(recorrido.getDato())) {
                return recorrido;
            }
            recorrido = recorrido.getSiguiente();
        }
        return null;
    }

    /** Devuelve el nodo de una posicion, recorriendo por el extremo mas cercano. */
    private NodoDoble<T> nodoEn(int posicion) {
        if (posicion < 0 || posicion >= tamanio) {
            throw new IndexOutOfBoundsException(
                    "Posicion " + posicion + " fuera de rango [0, " + (tamanio - 1) + "].");
        }
        NodoDoble<T> recorrido;
        if (posicion <= tamanio / 2) {
            recorrido = primero;
            for (int i = 0; i < posicion; i++) {
                recorrido = recorrido.getSiguiente();
            }
        } else {
            recorrido = primero;
            for (int i = tamanio; i > posicion; i--) {
                recorrido = recorrido.getAnterior();
            }
        }
        return recorrido;
    }

    private void exigirNoVacia() {
        if (estaVacia()) {
            throw new NoSuchElementException("La lista circular esta vacia.");
        }
    }
}
