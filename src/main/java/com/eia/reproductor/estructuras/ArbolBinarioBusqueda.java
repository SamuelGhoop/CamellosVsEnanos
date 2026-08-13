package com.eia.reproductor.estructuras;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Arbol Binario de Busqueda, implementado desde cero.
 *
 * <p>Estructura del modo de reproduccion alfabetica. Cada nodo cumple la invariante del ABB: todo
 * lo que cuelga a su izquierda es menor que el y todo lo que cuelga a su derecha es mayor, segun
 * el {@link Comparator} recibido en el constructor.</p>
 *
 * <p><b>Navegacion sobre el arbol vivo.</b> El modo alfabetico avanza con
 * {@link #sucesorInorden(Object)} y retrocede con {@link #predecesorInorden(Object)}. No se vuelca
 * el arbol a una lista para moverse por indices: cada paso se resuelve caminando por los enlaces
 * del arbol, apoyandose en el puntero al padre de {@link NodoArbol}. Asi, si se agrega o se elimina
 * una cancion, la navegacion se adapta sola sin reconstruir nada.</p>
 *
 * <p><b>Peor caso.</b> Si los elementos se insertan ya ordenados, cada nuevo nodo se cuelga siempre
 * del mismo lado y el arbol degenera en una lista enlazada de altura n. Ahi la busqueda pasa de
 * O(log n) a O(n). La solucion estandar es un arbol autobalanceado (AVL o rojo-negro), que rota los
 * nodos tras cada insercion para mantener la altura en O(log n). Para una biblioteca musical, donde
 * los titulos llegan en orden arbitrario, el ABB simple se comporta bien en la practica.</p>
 *
 * <p><b>Complejidades</b> (h = altura del arbol; O(log n) si esta equilibrado, O(n) si degenera):</p>
 * <table border="1">
 *   <caption>Costo de las operaciones</caption>
 *   <tr><th>Operacion</th><th>Costo</th><th>Motivo</th></tr>
 *   <tr><td>{@link #insertar(Object)}</td><td>O(h)</td><td>se baja por una sola rama</td></tr>
 *   <tr><td>{@link #eliminar(Object)}</td><td>O(h)</td><td>buscar el nodo y, si tiene dos hijos, su sucesor</td></tr>
 *   <tr><td>{@link #buscar(Object)}</td><td>O(h)</td><td>en cada nivel se descarta medio arbol</td></tr>
 *   <tr><td>{@link #minimo()} / {@link #maximo()}</td><td>O(h)</td><td>bajar siempre a la izquierda o a la derecha</td></tr>
 *   <tr><td>{@link #sucesorInorden(Object)}</td><td>O(h)</td><td>buscar el nodo y subir o bajar un tramo</td></tr>
 *   <tr><td>{@link #recorridoInorden()}</td><td>O(n)</td><td>visita cada nodo una vez</td></tr>
 *   <tr><td>{@link #tamanio()}</td><td>O(1)</td><td>se lleva un contador</td></tr>
 *   <tr><td>{@link #altura()}</td><td>O(n)</td><td>hay que mirar todas las ramas</td></tr>
 * </table>
 *
 * @param <T> tipo de los elementos almacenados
 */
public class ArbolBinarioBusqueda<T> implements Iterable<T> {

    /** Altura convenida para el arbol vacio; una hoja suelta tiene altura 0. */
    public static final int ALTURA_ARBOL_VACIO = -1;

    private final Comparator<T> comparador;
    private NodoArbol<T> raiz;
    private int tamanio;

    /**
     * Crea un arbol vacio con un criterio de orden explicito.
     *
     * <p>Se exige un {@link Comparator} en lugar de obligar a que {@code T} sea
     * {@link Comparable} para que la misma estructura sirva con distintos criterios (por titulo,
     * por artista, por calificacion) sin tocar el modelo.</p>
     *
     * @param comparador criterio de orden, no puede ser {@code null}
     * @throws NullPointerException si el comparador es {@code null}
     */
    public ArbolBinarioBusqueda(Comparator<T> comparador) {
        this.comparador = Objects.requireNonNull(comparador, "El comparador no puede ser nulo.");
        this.raiz = null;
        this.tamanio = 0;
    }

    // ------------------------------------------------------------------
    // Consultas basicas
    // ------------------------------------------------------------------

    /**
     * @return cantidad de elementos del arbol
     *         <p><b>Complejidad:</b> O(1).</p>
     */
    public int tamanio() {
        return tamanio;
    }

    /**
     * @return {@code true} si el arbol no tiene elementos
     *         <p><b>Complejidad:</b> O(1).</p>
     */
    public boolean estaVacio() {
        return tamanio == 0;
    }

    /**
     * Calcula la altura del arbol: la cantidad de aristas del camino mas largo de la raiz a una hoja.
     *
     * <p>El arbol vacio mide {@value #ALTURA_ARBOL_VACIO} y un arbol de un solo nodo mide 0.</p>
     *
     * <p><b>Complejidad:</b> O(n), porque no hay forma de saber cual es la rama mas larga sin
     * mirarlas todas.</p>
     *
     * @return la altura del arbol
     */
    public int altura() {
        return alturaDe(raiz);
    }

    // ------------------------------------------------------------------
    // Insercion
    // ------------------------------------------------------------------

    /**
     * Inserta un elemento respetando la invariante del ABB.
     *
     * <p>Se baja desde la raiz comparando: menor va a la izquierda, mayor a la derecha, hasta
     * encontrar un hueco. El nodo nuevo siempre entra como hoja.</p>
     *
     * <p><b>Los duplicados se rechazan.</b> Si el comparador devuelve 0 contra un elemento ya
     * presente, el nuevo no entra. Por eso el comparador de canciones desempata por artista y por
     * id: dos canciones distintas con el mismo titulo <i>deben</i> comparar distinto, o el arbol
     * perderia una.</p>
     *
     * <p><b>Complejidad:</b> O(h).</p>
     *
     * @param dato elemento a insertar, no puede ser {@code null}
     * @return {@code true} si se inserto, {@code false} si ya habia un elemento equivalente
     * @throws NullPointerException si el dato es {@code null}
     */
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

    // ------------------------------------------------------------------
    // Eliminacion
    // ------------------------------------------------------------------

    /**
     * Elimina un elemento del arbol resolviendo los tres casos clasicos.
     *
     * <ol>
     *   <li><b>Hoja:</b> se desconecta del padre y listo.</li>
     *   <li><b>Un solo hijo:</b> el hijo sube a ocupar el lugar del padre.</li>
     *   <li><b>Dos hijos:</b> se busca el <i>sucesor inorden</i> (el minimo del subarbol derecho,
     *       que por construccion no tiene hijo izquierdo) y ese sucesor toma el lugar del nodo
     *       eliminado. Se elige el sucesor y no cualquier otro nodo porque es el unico valor que
     *       mantiene la invariante: es mayor que todo el subarbol izquierdo y menor que el resto
     *       del derecho.</li>
     * </ol>
     *
     * <p>Se reubica el <b>nodo</b> completo en lugar de copiar el dato de un nodo a otro. Cuesta
     * un poco mas de codigo, pero mantiene la identidad de cada nodo y sus punteros al padre
     * coherentes, que es de lo que depende la navegacion inorden.</p>
     *
     * <p><b>Complejidad:</b> O(h).</p>
     *
     * @param dato elemento a eliminar
     * @return {@code true} si se elimino, {@code false} si no estaba en el arbol
     */
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

    /** Vacia el arbol por completo. <p><b>Complejidad:</b> O(1).</p> */
    public void limpiar() {
        raiz = null;
        tamanio = 0;
    }

    // ------------------------------------------------------------------
    // Busqueda y extremos
    // ------------------------------------------------------------------

    /**
     * Indica si un elemento esta en el arbol.
     *
     * <p><b>Complejidad:</b> O(h). En cada nivel se descarta medio arbol, que es exactamente la
     * ventaja del ABB sobre una lista.</p>
     *
     * @param dato elemento buscado
     * @return {@code true} si el arbol lo contiene
     */
    public boolean buscar(T dato) {
        return nodoDe(dato) != null;
    }

    /**
     * Devuelve el elemento mas pequenio, es decir la primera cancion en orden alfabetico.
     *
     * <p><b>Complejidad:</b> O(h): basta con bajar siempre a la izquierda.</p>
     *
     * @return el minimo del arbol
     * @throws NoSuchElementException si el arbol esta vacio
     */
    public T minimo() {
        exigirNoVacio();
        return minimoDe(raiz).getDato();
    }

    /**
     * Devuelve el elemento mas grande, es decir la ultima cancion en orden alfabetico.
     *
     * <p><b>Complejidad:</b> O(h): basta con bajar siempre a la derecha.</p>
     *
     * @return el maximo del arbol
     * @throws NoSuchElementException si el arbol esta vacio
     */
    public T maximo() {
        exigirNoVacio();
        return maximoDe(raiz).getDato();
    }

    // ------------------------------------------------------------------
    // Navegacion inorden
    // ------------------------------------------------------------------

    /**
     * Devuelve el elemento que sigue a otro en el recorrido inorden.
     *
     * <p>Dos casos:</p>
     * <ul>
     *   <li>Si el nodo <b>tiene subarbol derecho</b>, el sucesor es el minimo de ese subarbol: se
     *       baja una vez a la derecha y despues todo a la izquierda.</li>
     *   <li>Si <b>no tiene</b>, el sucesor es el primer ancestro del que se venga por la izquierda.
     *       Se sube por los punteros al padre hasta dejar de ser hijo derecho. Aqui es donde el
     *       enlace al padre resulta imprescindible.</li>
     * </ul>
     *
     * <p><b>Complejidad:</b> O(h).</p>
     *
     * @param dato elemento de referencia
     * @return el siguiente elemento en orden, o {@code null} si {@code dato} es el maximo o no esta
     */
    public T sucesorInorden(T dato) {
        NodoArbol<T> nodo = nodoDe(dato);
        if (nodo == null) {
            return null;
        }
        NodoArbol<T> sucesor = sucesorDe(nodo);
        return sucesor == null ? null : sucesor.getDato();
    }

    /**
     * Devuelve el elemento que precede a otro en el recorrido inorden.
     *
     * <p>Es la operacion simetrica de {@link #sucesorInorden(Object)}: si hay subarbol izquierdo,
     * el predecesor es su maximo; si no, es el primer ancestro del que se venga por la derecha.</p>
     *
     * <p><b>Complejidad:</b> O(h).</p>
     *
     * @param dato elemento de referencia
     * @return el elemento anterior en orden, o {@code null} si {@code dato} es el minimo o no esta
     */
    public T predecesorInorden(T dato) {
        NodoArbol<T> nodo = nodoDe(dato);
        if (nodo == null) {
            return null;
        }
        NodoArbol<T> predecesor = predecesorDe(nodo);
        return predecesor == null ? null : predecesor.getDato();
    }

    /**
     * Devuelve todos los elementos en orden ascendente.
     *
     * <p><b>Uso previsto: solo mostrar.</b> Sirve para llenar la lista de reproduccion en pantalla.
     * La navegacion del modo alfabetico <b>no</b> debe apoyarse en esta lista ni en sus indices:
     * para eso estan {@link #sucesorInorden(Object)} y {@link #predecesorInorden(Object)}, que
     * caminan sobre el arbol real.</p>
     *
     * <p>Se recorre de forma iterativa, arrancando en el minimo y encadenando sucesores. No hace
     * falta recursion ni una pila auxiliar porque los punteros al padre ya permiten volver hacia
     * arriba: el recorrido completo cuesta O(n) y usa memoria adicional constante.</p>
     *
     * <p><b>Complejidad:</b> O(n).</p>
     *
     * @return una lista con los elementos ordenados; vacia si el arbol esta vacio
     */
    public List<T> recorridoInorden() {
        List<T> elementos = new ArrayList<>(tamanio);
        for (NodoArbol<T> nodo = (raiz == null ? null : minimoDe(raiz));
             nodo != null;
             nodo = sucesorDe(nodo)) {
            elementos.add(nodo.getDato());
        }
        return elementos;
    }

    /**
     * Recorre el arbol en orden ascendente.
     *
     * <p><b>Complejidad:</b> O(n) el recorrido completo.</p>
     *
     * @return un iterador que entrega los elementos ordenados
     */
    @Override
    public Iterator<T> iterator() {
        return recorridoInorden().iterator();
    }

    @Override
    public String toString() {
        return recorridoInorden().toString();
    }

    // ------------------------------------------------------------------
    // Apoyo interno
    // ------------------------------------------------------------------

    /**
     * Reemplaza el subarbol enraizado en {@code viejo} por el enraizado en {@code nuevo}.
     *
     * <p>Solo arregla el enlace con el padre; los hijos los reacomoda quien lo llama.</p>
     */
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
