package com.eia.reproductor.estructuras;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Arbol Binario de Busqueda, implementado desde cero. */
public class ArbolBinarioBusqueda<T> implements Iterable<T> {
    /** Altura convenida para el arbol vacio; una hoja suelta tiene altura 0. */
    public static final int ALTURA_ARBOL_VACIO = -1;

    private final Comparator<T> comparador;
    private NodoArbol<T> raiz;
    private int tamanio;

    /** Crea un arbol vacio con un criterio de orden explicito. */
    public ArbolBinarioBusqueda(Comparator<T> comparador) {
        this.comparador = Objects.requireNonNull(comparador, "El comparador no puede ser nulo.");
        this.raiz = null;
        this.tamanio = 0;
    }

    // --- Consultas basicas ---

    /** @return cantidad de elementos del arbol. O(1). */
    public int tamanio() {
        return tamanio;
    }

    /** @return {@code true} si el arbol no tiene elementos. O(1). */
    public boolean estaVacio() {
        return tamanio == 0;
    }

    /**
     * Calcula la altura del arbol: la cantidad de aristas del camino mas largo de la raiz a una
     * hoja. O(n).
     */
    public int altura() {
        return alturaDe(raiz);
    }

    // --- Insercion ---

    /** Inserta un elemento respetando la invariante del ABB. O(h). */
    public boolean insertar(T dato) {
        Objects.requireNonNull(dato, "No se admiten elementos nulos en el arbol.");

        if (raiz == null) {
            raiz = new NodoArbol<>(dato);
            tamanio++;
            return true;
        }

        NodoArbol<T> recorrido = raiz;
        while (true) {
            int comparacion = comparador.compare(dato, recorrido.getDato());
            if (comparacion == 0) {
                return false;
            }
            if (comparacion < 0) {
                if (recorrido.getIzquierdo() == null) {
                    NodoArbol<T> nuevo = new NodoArbol<>(dato);
                    recorrido.setIzquierdo(nuevo);
                    nuevo.setPadre(recorrido);
                    tamanio++;
                    return true;
                }
                recorrido = recorrido.getIzquierdo();
            } else {
                if (recorrido.getDerecho() == null) {
                    NodoArbol<T> nuevo = new NodoArbol<>(dato);
                    recorrido.setDerecho(nuevo);
                    nuevo.setPadre(recorrido);
                    tamanio++;
                    return true;
                }
                recorrido = recorrido.getDerecho();
            }
        }
    }

    // --- Eliminacion ---

    /** Elimina un elemento del arbol resolviendo los tres casos clasicos. O(h). */
    public boolean eliminar(T dato) {
        NodoArbol<T> objetivo = nodoDe(dato);
        if (objetivo == null) {
            return false;
        }

        if (objetivo.getIzquierdo() == null) {
            // Casos 1 y 2: sin hijo izquierdo, sube el derecho (que puede ser null si era hoja).
            trasplantar(objetivo, objetivo.getDerecho());
        } else if (objetivo.getDerecho() == null) {
            // Caso 2 simetrico: solo tiene hijo izquierdo.
            trasplantar(objetivo, objetivo.getIzquierdo());
        } else {
            // Caso 3: dos hijos.
            NodoArbol<T> sucesor = minimoDe(objetivo.getDerecho());
            if (sucesor.getPadre() != objetivo) {
                // El sucesor esta mas abajo: primero se lo saca de su sitio poniendo en su lugar
                // a su hijo derecho, y despues se le cuelga el subarbol derecho del objetivo.
                trasplantar(sucesor, sucesor.getDerecho());
                sucesor.setDerecho(objetivo.getDerecho());
                sucesor.getDerecho().setPadre(sucesor);
            }
            trasplantar(objetivo, sucesor);
            sucesor.setIzquierdo(objetivo.getIzquierdo());
            sucesor.getIzquierdo().setPadre(sucesor);
        }

        objetivo.setPadre(null);
        objetivo.setIzquierdo(null);
        objetivo.setDerecho(null);
        tamanio--;
        return true;
    }

    /** Vacia el arbol por completo. O(1). */
    public void limpiar() {
        raiz = null;
        tamanio = 0;
    }

    // --- Busqueda y extremos ---

    /** Indica si un elemento esta en el arbol. O(h). */
    public boolean buscar(T dato) {
        return nodoDe(dato) != null;
    }

    /** Devuelve el elemento mas pequenio, es decir la primera cancion en orden alfabetico. O(h). */
    public T minimo() {
        exigirNoVacio();
        return minimoDe(raiz).getDato();
    }

    /** Devuelve el elemento mas grande, es decir la ultima cancion en orden alfabetico. O(h). */
    public T maximo() {
        exigirNoVacio();
        return maximoDe(raiz).getDato();
    }

    // --- Navegacion inorden ---

    /** Devuelve el elemento que sigue a otro en el recorrido inorden. O(h). */
    public T sucesorInorden(T dato) {
        NodoArbol<T> nodo = nodoDe(dato);
        if (nodo == null) {
            return null;
        }
        NodoArbol<T> sucesor = sucesorDe(nodo);
        return sucesor == null ? null : sucesor.getDato();
    }

    /** Devuelve el elemento que precede a otro en el recorrido inorden. O(h). */
    public T predecesorInorden(T dato) {
        NodoArbol<T> nodo = nodoDe(dato);
        if (nodo == null) {
            return null;
        }
        NodoArbol<T> predecesor = predecesorDe(nodo);
        return predecesor == null ? null : predecesor.getDato();
    }

    /** Devuelve la forma del arbol como una copia, para poder dibujarlo. O(n). */
    public <R> R forma(ConstructorDeRama<T, R> constructor) {
        return copiarForma(raiz, constructor);
    }

    private <R> R copiarForma(NodoArbol<T> nodo, ConstructorDeRama<T, R> constructor) {
        if (nodo == null) {
            return null;
        }
        return constructor.crear(
                nodo.getDato(),
                copiarForma(nodo.getIzquierdo(), constructor),
                copiarForma(nodo.getDerecho(), constructor));
    }

    /** Fabrica los nodos de la copia que devuelve {@link #forma}. */
    @FunctionalInterface
    public interface ConstructorDeRama<T, R> {
        /** Crea un nodo de la copia. */
        R crear(T dato, R izquierdo, R derecho);
    }

    /** Devuelve todos los elementos en orden ascendente. O(n). */
    public List<T> recorridoInorden() {
        List<T> elementos = new ArrayList<>(tamanio);
        for (NodoArbol<T> nodo = (raiz == null ? null : minimoDe(raiz));
             nodo != null;
             nodo = sucesorDe(nodo)) {
            elementos.add(nodo.getDato());
        }
        return elementos;
    }

    /** Recorre el arbol en orden ascendente. O(n). */
    @Override
    public Iterator<T> iterator() {
        return recorridoInorden().iterator();
    }

    @Override
    public String toString() {
        return recorridoInorden().toString();
    }

    // --- Apoyo interno ---

    /** Reemplaza el subarbol enraizado en {@code viejo} por el enraizado en {@code nuevo}. */
    private void trasplantar(NodoArbol<T> viejo, NodoArbol<T> nuevo) {
        if (viejo.getPadre() == null) {
            raiz = nuevo;
        } else if (viejo == viejo.getPadre().getIzquierdo()) {
            viejo.getPadre().setIzquierdo(nuevo);
        } else {
            viejo.getPadre().setDerecho(nuevo);
        }
        if (nuevo != null) {
            nuevo.setPadre(viejo.getPadre());
        }
    }

    /** Busca el nodo que contiene un dato bajando por una sola rama, o {@code null} si no esta. */
    private NodoArbol<T> nodoDe(T dato) {
        if (dato == null) {
            return null;
        }
        NodoArbol<T> recorrido = raiz;
        while (recorrido != null) {
            int comparacion = comparador.compare(dato, recorrido.getDato());
            if (comparacion == 0) {
                return recorrido;
            }
            recorrido = (comparacion < 0) ? recorrido.getIzquierdo() : recorrido.getDerecho();
        }
        return null;
    }

    private NodoArbol<T> minimoDe(NodoArbol<T> desde) {
        NodoArbol<T> recorrido = desde;
        while (recorrido.getIzquierdo() != null) {
            recorrido = recorrido.getIzquierdo();
        }
        return recorrido;
    }

    private NodoArbol<T> maximoDe(NodoArbol<T> desde) {
        NodoArbol<T> recorrido = desde;
        while (recorrido.getDerecho() != null) {
            recorrido = recorrido.getDerecho();
        }
        return recorrido;
    }

    private NodoArbol<T> sucesorDe(NodoArbol<T> nodo) {
        if (nodo.getDerecho() != null) {
            return minimoDe(nodo.getDerecho());
        }
        NodoArbol<T> ancestro = nodo.getPadre();
        NodoArbol<T> hijo = nodo;
        while (ancestro != null && hijo == ancestro.getDerecho()) {
            hijo = ancestro;
            ancestro = ancestro.getPadre();
        }
        return ancestro;
    }

    private NodoArbol<T> predecesorDe(NodoArbol<T> nodo) {
        if (nodo.getIzquierdo() != null) {
            return maximoDe(nodo.getIzquierdo());
        }
        NodoArbol<T> ancestro = nodo.getPadre();
        NodoArbol<T> hijo = nodo;
        while (ancestro != null && hijo == ancestro.getIzquierdo()) {
            hijo = ancestro;
            ancestro = ancestro.getPadre();
        }
        return ancestro;
    }

    private int alturaDe(NodoArbol<T> nodo) {
        if (nodo == null) {
            return ALTURA_ARBOL_VACIO;
        }
        return 1 + Math.max(alturaDe(nodo.getIzquierdo()), alturaDe(nodo.getDerecho()));
    }

    private void exigirNoVacio() {
        if (estaVacio()) {
            throw new NoSuchElementException("El arbol binario de busqueda esta vacio.");
        }
    }
}
