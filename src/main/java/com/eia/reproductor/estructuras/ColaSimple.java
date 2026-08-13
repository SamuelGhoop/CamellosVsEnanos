package com.eia.reproductor.estructuras;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Cola simple FIFO, implementada desde cero.
 *
 * <p>Estructura del modo de reproduccion por orden de llegada. Se atiende primero al que llego
 * primero: <b>First In, First Out</b>.</p>
 *
 * <p>Se mantienen dos referencias, {@code frente} y {@code fin}, y por eso las dos operaciones
 * propias de la cola son O(1). Si solo se guardara el frente, encolar obligaria a recorrer toda la
 * cadena hasta el ultimo nodo y costaria O(n).</p>
 *
 * <p><b>{@link #desencolar()} saca el elemento de verdad:</b> desenlaza el nodo del frente y
 * reduce el tamanio. No es un indice que avanza sobre una coleccion intacta. Por eso, en el modo
 * de orden de llegada, una cancion ya reproducida desaparece de la cola y solo vuelve si se
 * recarga la cola completa desde la biblioteca.</p>
 *
 * <p><b>Complejidades:</b></p>
 * <table border="1">
 *   <caption>Costo de las operaciones</caption>
 *   <tr><th>Operacion</th><th>Costo</th><th>Motivo</th></tr>
 *   <tr><td>{@link #encolar(Object)}</td><td>O(1)</td><td>se engancha al nodo {@code fin}</td></tr>
 *   <tr><td>{@link #desencolar()}</td><td>O(1)</td><td>se suelta el nodo {@code frente}</td></tr>
 *   <tr><td>{@link #verFrente()}</td><td>O(1)</td><td>lectura directa</td></tr>
 *   <tr><td>{@link #buscar(Object)}</td><td>O(n)</td><td>recorrido lineal; no es su operacion natural</td></tr>
 *   <tr><td>{@link #tamanio()}</td><td>O(1)</td><td>se lleva un contador</td></tr>
 * </table>
 *
 * @param <T> tipo de los elementos almacenados
 */
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

    // ------------------------------------------------------------------
    // Operaciones propias de la cola
    // ------------------------------------------------------------------

    /**
     * Agrega un elemento al final de la cola.
     *
     * <p><b>Complejidad:</b> O(1).</p>
     *
     * @param dato elemento a encolar, no puede ser {@code null}
     * @throws NullPointerException si el dato es {@code null}
     */
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

    /**
     * Retira y devuelve el elemento del frente.
     *
     * <p>El nodo sale de la estructura: el tamanio baja y el elemento no se puede recuperar. Es
     * exactamente el comportamiento que pide el enunciado para el modo de orden de llegada.</p>
     *
     * <p><b>Complejidad:</b> O(1).</p>
     *
     * @return el elemento que estaba al frente
     * @throws NoSuchElementException si la cola esta vacia
     */
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

    /**
     * Consulta el elemento del frente sin retirarlo.
     *
     * <p><b>Complejidad:</b> O(1).</p>
     *
     * @return el elemento que esta al frente
     * @throws NoSuchElementException si la cola esta vacia
     */
    public T verFrente() {
        exigirNoVacia();
        return frente.getDato();
    }

    /**
     * Consulta el ultimo elemento encolado sin retirarlo.
     *
     * <p><b>Complejidad:</b> O(1).</p>
     *
     * @return el elemento del final de la cola
     * @throws NoSuchElementException si la cola esta vacia
     */
    public T verFin() {
        exigirNoVacia();
        return fin.getDato();
    }

    // ------------------------------------------------------------------
    // Consultas generales
    // ------------------------------------------------------------------

    /**
     * @return cantidad de elementos en la cola
     *         <p><b>Complejidad:</b> O(1).</p>
     */
    public int tamanio() {
        return tamanio;
    }

    /**
     * @return {@code true} si la cola no tiene elementos
     *         <p><b>Complejidad:</b> O(1).</p>
     */
    public boolean estaVacia() {
        return tamanio == 0;
    }

    /**
     * Indica si un elemento esta en la cola.
     *
     * <p><b>Complejidad:</b> O(n). Buscar no es una operacion natural de una cola: si el problema
     * exige buscar seguido, la cola es la estructura equivocada.</p>
     *
     * @param dato elemento buscado
     * @return {@code true} si la cola lo contiene
     */
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

    /**
     * Vacia la cola por completo.
     *
     * <p><b>Complejidad:</b> O(1): se sueltan las dos referencias y el recolector de basura hace
     * el resto.</p>
     */
    public void limpiar() {
        frente = null;
        fin = null;
        tamanio = 0;
    }

    // ------------------------------------------------------------------
    // Iterable
    // ------------------------------------------------------------------

    /**
     * Recorre la cola del frente hacia el fin <b>sin desencolar</b>.
     *
     * <p>Sirve para que la interfaz pueda mostrar la lista de espera sin consumirla.</p>
     *
     * <p><b>Complejidad:</b> O(n) el recorrido completo, O(1) cada paso.</p>
     *
     * @return un iterador de solo lectura sobre la cola
     */
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
