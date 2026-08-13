package com.eia.reproductor.estructuras;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;

/**
 * Lista ligada circular doble, implementada desde cero.
 *
 * <p>Estructura del modo de reproduccion aleatoria y tambien de la biblioteca maestra.</p>
 *
 * <p><b>Invariante central:</b> los enlaces nunca valen {@code null}. El ultimo nodo apunta al
 * primero y el primero apunta al ultimo, de modo que recorrerla en cualquiera de las dos
 * direcciones da vueltas indefinidamente. Con un solo elemento, el nodo se apunta a si mismo.
 * Esto es lo que hace que la reproduccion no tenga final: al pasar de la ultima cancion se vuelve
 * a la primera sin ningun caso especial en el codigo que la recorre.</p>
 *
 * <p><b>Complejidades:</b></p>
 * <table border="1">
 *   <caption>Costo de las operaciones</caption>
 *   <tr><th>Operacion</th><th>Costo</th><th>Motivo</th></tr>
 *   <tr><td>{@link #agregar(Object)}</td><td>O(1)</td><td>se inserta justo antes del primero</td></tr>
 *   <tr><td>{@link #agregarEnPosicion(int, Object)}</td><td>O(n)</td><td>hay que llegar a la posicion</td></tr>
 *   <tr><td>{@link #eliminar(Object)}</td><td>O(n)</td><td>buscar es lineal; desenlazar es O(1)</td></tr>
 *   <tr><td>{@link #buscar(Object)}</td><td>O(n)</td><td>no hay orden que permita descartar mitades</td></tr>
 *   <tr><td>{@link #obtener(int)}</td><td>O(n)</td><td>sin acceso aleatorio, aunque se recorre por el lado mas corto</td></tr>
 *   <tr><td>{@link #tamanio()}</td><td>O(1)</td><td>se lleva un contador</td></tr>
 *   <tr><td>{@link #mezclar()}</td><td>O(n)</td><td>Fisher-Yates y un reenlace lineal</td></tr>
 * </table>
 *
 * @param <T> tipo de los elementos almacenados
 */
public class ListaCircularDoble<T> implements Iterable<T> {

    private NodoDoble<T> primero;
    private int tamanio;

    /** Crea una lista vacia. */
    public ListaCircularDoble() {
        this.primero = null;
        this.tamanio = 0;
    }

    // ------------------------------------------------------------------
    // Consultas basicas
    // ------------------------------------------------------------------

    /**
     * @return cantidad de elementos de la lista
     *         <p><b>Complejidad:</b> O(1), el contador se mantiene en cada alta y baja.</p>
     */
    public int tamanio() {
        return tamanio;
    }

    /**
     * @return {@code true} si la lista no tiene elementos
     *         <p><b>Complejidad:</b> O(1).</p>
     */
    public boolean estaVacia() {
        return tamanio == 0;
    }

    // ------------------------------------------------------------------
    // Altas
    // ------------------------------------------------------------------

    /**
     * Agrega un elemento al final de la lista.
     *
     * <p>En una lista circular "el final" es la posicion inmediatamente anterior al primero, asi
     * que no hace falta recorrer nada: se inserta entre {@code primero.anterior} y
     * {@code primero}.</p>
     *
     * <p><b>Complejidad:</b> O(1).</p>
     *
     * @param dato elemento a agregar, no puede ser {@code null}
     * @throws NullPointerException si el dato es {@code null}
     */
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

    /**
     * Inserta un elemento en una posicion concreta.
     *
     * <p><b>Complejidad:</b> O(n) por el recorrido hasta la posicion.</p>
     *
     * @param posicion indice donde queda el nuevo elemento, entre 0 y {@link #tamanio()} inclusive
     * @param dato     elemento a insertar, no puede ser {@code null}
     * @throws IndexOutOfBoundsException si la posicion esta fuera de rango
     * @throws NullPointerException      si el dato es {@code null}
     */
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

    // ------------------------------------------------------------------
    // Bajas
    // ------------------------------------------------------------------

    /**
     * Elimina la primera aparicion de un elemento.
     *
     * <p><b>Complejidad:</b> O(n). Encontrar el nodo es lineal, pero desenlazarlo es O(1) porque
     * el nodo ya conoce a sus dos vecinos: esta es la ventaja concreta de que la lista sea doble
     * y no simple.</p>
     *
     * @param dato elemento a eliminar
     * @return {@code true} si se elimino, {@code false} si no estaba en la lista
     */
    public boolean eliminar(T dato) {
        NodoDoble<T> objetivo = nodoDe(dato);
        if (objetivo == null) {
            return false;
        }
        desenlazar(objetivo);
        return true;
    }

    /**
     * Deja la lista vacia.
     *
     * <p><b>Complejidad:</b> O(1). Basta con soltar la referencia al primer nodo: al quedar el
     * anillo sin raices externas, el recolector de basura se lleva la cadena completa.</p>
     */
    public void limpiar() {
        primero = null;
        tamanio = 0;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    /**
     * Indica si un elemento esta en la lista.
     *
     * <p>La comparacion usa {@code equals}, no identidad de referencia.</p>
     *
     * <p><b>Complejidad:</b> O(n).</p>
     *
     * @param dato elemento buscado
     * @return {@code true} si la lista lo contiene
     */
    public boolean buscar(T dato) {
        return nodoDe(dato) != null;
    }

    /**
     * Devuelve el elemento que esta en una posicion.
     *
     * <p><b>Complejidad:</b> O(n) en el peor caso, pero solo se recorre <i>media</i> lista: si el
     * indice esta en la segunda mitad se camina hacia atras desde el final. Es otra ventaja de
     * tener enlaces en ambos sentidos.</p>
     *
     * @param posicion indice del elemento, entre 0 y {@code tamanio - 1}
     * @return el elemento en esa posicion
     * @throws IndexOutOfBoundsException si la posicion esta fuera de rango
     */
    public T obtener(int posicion) {
        return nodoEn(posicion).getDato();
    }

    /**
     * Devuelve el primer elemento de la lista.
     *
     * <p><b>Complejidad:</b> O(1).</p>
     *
     * @return el primer elemento
     * @throws NoSuchElementException si la lista esta vacia
     */
    public T primero() {
        exigirNoVacia();
        return primero.getDato();
    }

    /**
     * Devuelve el ultimo elemento de la lista.
     *
     * <p><b>Complejidad:</b> O(1), porque el ultimo es simplemente el anterior al primero.</p>
     *
     * @return el ultimo elemento
     * @throws NoSuchElementException si la lista esta vacia
     */
    public T ultimo() {
        exigirNoVacia();
        return primero.getAnterior().getDato();
    }

    // ------------------------------------------------------------------
    // Mezcla
    // ------------------------------------------------------------------

    /**
     * Baraja la lista con el algoritmo de Fisher-Yates.
     *
     * <p><b>Complejidad:</b> O(n) en tiempo y O(n) en memoria auxiliar (un arreglo de referencias
     * a los nodos).</p>
     *
     * <p>Se barajan las <i>referencias a los nodos</i> y despues se reconstruyen los enlaces, en
     * vez de mover los datos de un nodo a otro. Asi cada nodo conserva su dato y cualquier
     * {@link Cursor} que apunte a un nodo sigue apuntando a la misma cancion, solo que ahora tiene
     * vecinos distintos.</p>
     */
    public void mezclar() {
        mezclar(new Random());
    }

    /**
     * Baraja la lista usando una fuente de aleatoriedad concreta.
     *
     * <p>Existe para que las pruebas puedan sembrar el generador y obtener resultados
     * reproducibles.</p>
     *
     * <p><b>Complejidad:</b> O(n).</p>
     *
     * @param aleatorio generador de numeros aleatorios
     * @throws NullPointerException si el generador es {@code null}
     */
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
        // equiprobables, cosa que el "intercambio con posicion cualquiera" no cumple.
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

    // ------------------------------------------------------------------
    // Cursor
    // ------------------------------------------------------------------

    /**
     * Crea un cursor posicionado en el primer elemento.
     *
     * <p>El cursor es la forma en que el modo de reproduccion aleatoria navega la lista. Se expone
     * un cursor en lugar de los nodos para que las capas superiores nunca toquen la estructura
     * interna: {@link NodoDoble} no es visible fuera de este paquete.</p>
     *
     * <p><b>Complejidad:</b> O(1).</p>
     *
     * @return un cursor nuevo apuntando al primer elemento
     * @throws NoSuchElementException si la lista esta vacia
     */
    public Cursor<T> nuevoCursor() {
        exigirNoVacia();
        return new Cursor<>(this, primero);
    }

    /**
     * Puntero navegable sobre una {@link ListaCircularDoble}.
     *
     * <p>Avanza y retrocede en O(1) y nunca se queda sin elementos: al pasar del ultimo vuelve al
     * primero y al retroceder desde el primero salta al ultimo.</p>
     *
     * <p><b>Advertencia:</b> si se elimina de la lista el elemento sobre el que esta parado el
     * cursor, hay que reposicionarlo con {@link #posicionarEn(Object)}. El cursor no se entera
     * solo de las bajas.</p>
     *
     * @param <T> tipo de los elementos recorridos
     */
    public static final class Cursor<T> {

        private final ListaCircularDoble<T> lista;
        private NodoDoble<T> actual;

        private Cursor(ListaCircularDoble<T> lista, NodoDoble<T> actual) {
            this.lista = lista;
            this.actual = actual;
        }

        /**
         * @return el elemento sobre el que esta parado el cursor
         *         <p><b>Complejidad:</b> O(1).</p>
         */
        public T actual() {
            return actual.getDato();
        }

        /**
         * Avanza una posicion y devuelve el elemento al que llego.
         *
         * <p><b>Complejidad:</b> O(1).</p>
         *
         * @return el siguiente elemento; tras el ultimo devuelve el primero
         */
        public T siguiente() {
            actual = actual.getSiguiente();
            return actual.getDato();
        }

        /**
         * Retrocede una posicion y devuelve el elemento al que llego.
         *
         * <p><b>Complejidad:</b> O(1).</p>
         *
         * @return el elemento anterior; antes del primero devuelve el ultimo
         */
        public T anterior() {
            actual = actual.getAnterior();
            return actual.getDato();
        }

        /**
         * Mueve el cursor al nodo que contiene un elemento dado.
         *
         * <p><b>Complejidad:</b> O(n).</p>
         *
         * @param dato elemento sobre el que se quiere parar
         * @return {@code true} si el elemento existia y el cursor se movio
         */
        public boolean posicionarEn(T dato) {
            NodoDoble<T> objetivo = lista.nodoDe(dato);
            if (objetivo == null) {
                return false;
            }
            actual = objetivo;
            return true;
        }

        /**
         * Devuelve el cursor al primer elemento de la lista.
         *
         * <p><b>Complejidad:</b> O(1).</p>
         *
         * @throws NoSuchElementException si la lista quedo vacia
         */
        public void reiniciar() {
            lista.exigirNoVacia();
            actual = lista.primero;
        }
    }

    // ------------------------------------------------------------------
    // Iterable
    // ------------------------------------------------------------------

    /**
     * Recorre la lista una sola vuelta, del primero al ultimo.
     *
     * <p>El iterador se corta por <b>cantidad de elementos visitados</b>, no por encontrar un
     * {@code null}: en una lista circular no hay ningun enlace nulo donde detenerse, asi que un
     * iterador ingenuo daria vueltas para siempre. Este detalle es la trampa clasica de esta
     * estructura.</p>
     *
     * <p><b>Complejidad:</b> O(n) recorrer la lista completa, O(1) cada paso.</p>
     *
     * @return un iterador acotado al tamanio de la lista
     */
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

    // ------------------------------------------------------------------
    // Apoyo interno
    // ------------------------------------------------------------------

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
