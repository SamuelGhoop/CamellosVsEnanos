package com.eia.reproductor.modos;

import com.eia.reproductor.estructuras.ArbolBinarioBusqueda;
import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.EstructuraVisual;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Modo 3: reproduccion alfabetica sobre un {@link ArbolBinarioBusqueda}. */
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
        Cancion reemplazo = arbol.sucesorInorden(cancion);
        if (reemplazo == null) {
            reemplazo = arbol.predecesorInorden(cancion);
        }
        arbol.eliminar(cancion);
        establecerActual(arbol.estaVacio() ? null : reemplazo);
    }

    /**
     * {@inheritDoc} Este es el unico modo que reacciona a la edicion, porque es el unico cuya
     * estructura coloca las canciones segun sus datos.
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
     * {@inheritDoc} Se reinserta con el titulo nuevo, con lo que el arbol la coloca sola en su
     * posicion alfabetica correcta.
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

    /** @return la altura del arbol, para mostrarla en la interfaz junto al nombre de la estructura */
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
     * {@inheritDoc} La forma real del arbol, no el recorrido aplanado: se ve como se ramifica y, si
     * las canciones entraron ya ordenadas, se ve degenerar en una sola rama.
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
