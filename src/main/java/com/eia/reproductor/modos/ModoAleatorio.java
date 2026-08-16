package com.eia.reproductor.modos;

import com.eia.reproductor.estructuras.ListaCircularDoble;
import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.EstructuraVisual;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Modo 1: reproduccion aleatoria sobre una {@link ListaCircularDoble}. */
public class ModoAleatorio extends ModoBase {
    private static final String NOMBRE = "Aleatorio";
    private static final String ESTRUCTURA = "Lista Ligada Circular Doble";

    private final ListaCircularDoble<Cancion> lista = new ListaCircularDoble<>();
    private final Random aleatorio;
    private ListaCircularDoble.Cursor<Cancion> cursor;

    /** Crea el modo con una fuente de aleatoriedad normal. */
    public ModoAleatorio() {
        this(new Random());
    }

    /** Crea el modo con una fuente de aleatoriedad concreta. */
    public ModoAleatorio(Random aleatorio) {
        this.aleatorio = Objects.requireNonNull(aleatorio, "El generador aleatorio no puede ser nulo.");
    }

    @Override
    public void cargar(Iterable<Cancion> canciones) {
        Objects.requireNonNull(canciones, "La coleccion de canciones no puede ser nula.");
        lista.limpiar();
        for (Cancion cancion : canciones) {
            lista.agregar(cancion);
        }
        lista.mezclar(aleatorio);
        cursor = lista.estaVacia() ? null : lista.nuevoCursor();
        establecerActual(null);
    }

    @Override
    protected Cancion calcularSiguiente() {
        if (actual() == null) {
            // Todavia no ha empezado la reproduccion: suena la primera del orden barajado.
            cursor.reiniciar();
            return cursor.actual();
        }
        return cursor.siguiente();
    }

    @Override
    protected Cancion calcularAnterior() {
        if (actual() == null) {
            cursor.reiniciar();
            return cursor.actual();
        }
        return cursor.anterior();
    }

    @Override
    protected void reiniciarNavegacion() {
        if (cursor != null) {
            cursor.reiniciar();
        }
    }

    /** Vuelve a barajar la coleccion sin interrumpir lo que suena. O(n). */
    public void volverAMezclar() {
        if (lista.tamanio() < 2) {
            return;
        }
        Cancion enCurso = actual();
        lista.mezclar(aleatorio);
        cursor = lista.nuevoCursor();
        if (enCurso != null) {
            cursor.posicionarEn(enCurso);
        }
    }

    @Override
    public boolean permiteAnterior() {
        return true;
    }

    @Override
    public boolean hayMas() {
        // Al ser circular, mientras haya al menos una cancion siempre hay una siguiente.
        return !lista.estaVacia();
    }

    @Override
    public void agregar(Cancion cancion) {
        Objects.requireNonNull(cancion, "La cancion no puede ser nula.");
        if (lista.buscar(cancion)) {
            return;
        }
        lista.agregar(cancion);
        if (cursor == null) {
            cursor = lista.nuevoCursor();
        }
    }

    @Override
    public void eliminar(Cancion cancion) {
        if (cancion == null || !lista.buscar(cancion)) {
            return;
        }

        if (lista.tamanio() == 1) {
            lista.limpiar();
            cursor = null;
            establecerActual(null);
            return;
        }

        boolean sonabaEsta = cancion.equals(actual());
        // Hay que sacar el cursor del nodo que esta a punto de desaparecer, o quedaria colgando
        // sobre un nodo que ya no pertenece al anillo.
        if (cursor != null && cancion.equals(cursor.actual())) {
            cursor.siguiente();
        }
        lista.eliminar(cancion);
        if (sonabaEsta) {
            establecerActual(cursor == null ? null : cursor.actual());
        }
    }

    @Override
    public List<Cancion> listaReproduccion() {
        List<Cancion> orden = new ArrayList<>(lista.tamanio());
        for (Cancion cancion : lista) {
            orden.add(cancion);
        }
        return orden;
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
     * {@inheritDoc} El anillo en el orden en que quedaron enlazados los nodos tras la mezcla, con
     * la posicion del cursor.
     */
    @Override
    public EstructuraVisual estructuraVisual() {
        List<Cancion> orden = listaReproduccion();
        List<String> etiquetas = new ArrayList<>(orden.size());
        int indice = -1;
        for (int i = 0; i < orden.size(); i++) {
            etiquetas.add(orden.get(i).getTitulo());
            if (orden.get(i).equals(actual())) {
                indice = i;
            }
        }
        return new EstructuraVisual.Anillo(ESTRUCTURA, etiquetas, indice);
    }
}
