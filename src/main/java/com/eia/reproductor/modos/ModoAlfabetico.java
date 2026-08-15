package com.eia.reproductor.modos;

import com.eia.reproductor.estructuras.ArbolBinarioBusqueda;
import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.EstructuraVisual;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Modo 3: reproduccion alfabetica sobre un {@link ArbolBinarioBusqueda}.
 *
 * <p>Las canciones se insertan en el arbol usando {@link Cancion#POR_TITULO}, que compara titulos
 * con las reglas del espanol. La reproduccion simula el recorrido inorden: avanzar es ir al
 * <b>sucesor inorden</b> y retroceder es ir al <b>predecesor inorden</b>.</p>
 *
 * <p><b>La navegacion no aplana el arbol.</b> No existe ninguna lista interna con las canciones
 * ordenadas por la que se avance con un indice. Cada paso se resuelve caminando por los enlaces del
 * arbol: si el nodo tiene subarbol derecho, el sucesor es el minimo de ese subarbol; si no lo
 * tiene, se sube por los punteros al padre hasta el primer ancestro del que se venga por la
 * izquierda. La consecuencia practica es que agregar o eliminar una cancion no obliga a reconstruir
 * nada: el recorrido se adapta solo.</p>
 *
 * <p><b>Que pasa al llegar al final.</b> Cuando se avanza desde la ultima cancion, la reproduccion
 * <b>vuelve a la primera</b>, y al retroceder desde la primera se salta a la ultima. Se eligio el
 * comportamiento circular en lugar de detenerse porque un reproductor que se congela en la ultima
 * pista es peor experiencia de uso. Es una decision del modo, no del arbol: el arbol responde
 * {@code null} cuando no hay sucesor y es este modo el que decide dar la vuelta.</p>
 *
 * <p><b>Por que un arbol y no una lista ordenada.</b> Mantener el orden alfabetico en una lista
 * obligaria a recorrerla entera para encontrar donde insertar cada cancion nueva, O(n). El arbol
 * inserta y busca en O(h), que con titulos que llegan en orden arbitrario es del orden de
 * O(log n). El precio es el peor caso: si las canciones se insertaran ya ordenadas alfabeticamente,
 * el arbol degeneraria en una lista de altura n.</p>
 */
public class ModoAlfabetico extends ModoBase {

    private static final String NOMBRE = "Alfabético";
    private static final String ESTRUCTURA = "Árbol Binario de Búsqueda";

    private final ArbolBinarioBusqueda<Cancion> arbol =
            new ArbolBinarioBusqueda<>(Cancion.POR_TITULO);

    /** Marca si la cancion que se esta editando estaba realmente en este arbol. */
    private boolean estabaEnElArbol;

    /** Marca si la cancion que se esta editando era la que sonaba. */
    private boolean editabaLaQueSonaba;

    @Override
    public void cargar(Iterable<Cancion> canciones) {
        Objects.requireNonNull(canciones, "La coleccion de canciones no puede ser nula.");
        arbol.limpiar();
        for (Cancion cancion : canciones) {
            arbol.insertar(cancion);
        }
        establecerActual(null);
    }

    @Override
    protected Cancion calcularSiguiente() {
        if (actual() == null) {
            // Aun no ha empezado la reproduccion: se arranca por la primera en orden alfabetico.
            return arbol.minimo();
        }
        Cancion sucesor = arbol.sucesorInorden(actual());
        // sucesor == null significa que la actual era la ultima: se da la vuelta al principio.
        return sucesor != null ? sucesor : arbol.minimo();
    }

    @Override
    protected Cancion calcularAnterior() {
        if (actual() == null) {
            return arbol.maximo();
        }
        Cancion predecesor = arbol.predecesorInorden(actual());
        return predecesor != null ? predecesor : arbol.maximo();
    }

    @Override
    protected void reiniciarNavegacion() {
        // El arbol no guarda posicion: la posicion es la cancion actual, que ModoBase ya limpio.
    }

    @Override
    public boolean permiteAnterior() {
        return true;
    }

    @Override
    public boolean hayMas() {
        return !arbol.estaVacio();
    }

    @Override
    public void agregar(Cancion cancion) {
        Objects.requireNonNull(cancion, "La cancion no puede ser nula.");
        arbol.insertar(cancion);
    }

    @Override
    public void eliminar(Cancion cancion) {
        if (cancion == null || !arbol.buscar(cancion)) {
            return;
        }

        if (!cancion.equals(actual())) {
            arbol.eliminar(cancion);
            return;
        }

        // Se esta eliminando justo la cancion que suena, que es el punto de apoyo de la
        // navegacion. Hay que averiguar a donde saltar ANTES de sacarla del arbol: despues ya no
        // se le puede pedir su sucesor porque no estaria.
        Cancion reemplazo = arbol.sucesorInorden(cancion);
        if (reemplazo == null) {
            reemplazo = arbol.predecesorInorden(cancion);
        }
        arbol.eliminar(cancion);
        establecerActual(arbol.estaVacio() ? null : reemplazo);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Este es el unico modo que reacciona a la edicion, porque es el unico cuya estructura
     * coloca las canciones segun sus datos. Se retira la cancion del arbol <b>mientras su titulo
     * sigue siendo el viejo</b>, que es la unica ventana en la que el arbol todavia sabe donde
     * esta.</p>
     */
    @Override
    public void prepararEdicion(Cancion cancion) {
        if (cancion == null || !arbol.buscar(cancion)) {
            estabaEnElArbol = false;
            return;
        }
        estabaEnElArbol = true;
        editabaLaQueSonaba = cancion.equals(actual());
        arbol.eliminar(cancion);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Se reinserta con el titulo nuevo, con lo que el arbol la coloca sola en su posicion
     * alfabetica correcta. Si era la cancion que sonaba, se conserva como actual.</p>
     */
    @Override
    public void confirmarEdicion(Cancion cancion) {
        if (!estabaEnElArbol) {
            return;
        }
        arbol.insertar(cancion);
        if (editabaLaQueSonaba) {
            establecerActual(cancion);
        }
        estabaEnElArbol = false;
        editabaLaQueSonaba = false;
    }

    @Override
    public List<Cancion> listaReproduccion() {
        return arbol.recorridoInorden();
    }

    /**
     * @return la altura del arbol, para mostrarla en la interfaz junto al nombre de la estructura
     */
    public int alturaDelArbol() {
        return arbol.altura();
    }

    @Override
    public String nombre() {
        return NOMBRE;
    }

    @Override
    public String estructuraUsada() {
        return ESTRUCTURA;
    }

    /**
     * {@inheritDoc}
     *
     * <p>La forma real del arbol, no el recorrido aplanado: se ve como se ramifica y, si las
     * canciones entraron ya ordenadas, se ve degenerar en una sola rama.</p>
     */
    @Override
    public EstructuraVisual estructuraVisual() {
        EstructuraVisual.Rama raiz = arbol.forma(
                (dato, izquierdo, derecho) ->
                        new EstructuraVisual.Rama(dato.getTitulo(), izquierdo, derecho));
        Cancion sonando = actual();
        return new EstructuraVisual.Arbol(
                ESTRUCTURA, raiz, sonando == null ? null : sonando.getTitulo());
    }
}
