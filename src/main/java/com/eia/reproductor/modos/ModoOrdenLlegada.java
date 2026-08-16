package com.eia.reproductor.modos;

import com.eia.reproductor.estructuras.ColaSimple;
import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.EstructuraVisual;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Modo 2: reproduccion por orden de llegada sobre una {@link ColaSimple}. */
public class ModoOrdenLlegada extends ModoBase {
    private static final String NOMBRE = "Orden de llegada";
    private static final String ESTRUCTURA = "Cola Simple (FIFO)";

    private ColaSimple<Cancion> cola = new ColaSimple<>();

    @Override
    public void cargar(Iterable<Cancion> canciones) {
        Objects.requireNonNull(canciones, "La coleccion de canciones no puede ser nula.");
        cola.limpiar();
        for (Cancion cancion : canciones) {
            cola.encolar(cancion);
        }
        establecerActual(null);
    }

    @Override
    protected Cancion calcularSiguiente() {
        // Aqui esta el corazon del modo: la cancion se retira de la estructura, no se "pasa por
        // encima" de ella con un indice.
        return cola.desencolar();
    }

    // No se sobrescribe calcularAnterior(): la implementacion de ModoBase ya falla, y de todos
    // modos anterior() nunca llega hasta ahi porque permiteAnterior() devuelve false.

    @Override
    protected void reiniciarNavegacion() {
        // Las canciones ya desencoladas no se pueden recuperar: salieron de la estructura. Para
        // volver a llenar la cola hay que llamar cargar() con la biblioteca.
    }

    @Override
    public boolean permiteAnterior() {
        return false;
    }

    @Override
    public boolean hayMas() {
        return !cola.estaVacia();
    }

    @Override
    public void agregar(Cancion cancion) {
        Objects.requireNonNull(cancion, "La cancion no puede ser nula.");
        cola.encolar(cancion);
    }

    @Override
    public void eliminar(Cancion cancion) {
        if (cancion == null || !cola.buscar(cancion)) {
            return;
        }
        // Sacar un elemento del medio no es una operacion de cola, asi que en lugar de agregarle a
        // ColaSimple un metodo ajeno a su contrato, se reconstruye usando solo encolar y
        ColaSimple<Cancion> reconstruida = new ColaSimple<>();
        while (!cola.estaVacia()) {
            Cancion actual = cola.desencolar();
            if (!actual.equals(cancion)) {
                reconstruida.encolar(actual);
            }
        }
        cola = reconstruida;
    }

    @Override
    public List<Cancion> listaReproduccion() {
        // El iterador de ColaSimple recorre sin desencolar, asi que mostrar la lista de espera
        // no consume la cola.
        List<Cancion> pendientes = new ArrayList<>(cola.tamanio());
        for (Cancion cancion : cola) {
            pendientes.add(cancion);
        }
        return pendientes;
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
     * {@inheritDoc} Lo que queda en la cola, con el frente a la izquierda, y cuantas ya salieron.
     */
    @Override
    public EstructuraVisual estructuraVisual() {
        List<Cancion> pendientes = listaReproduccion();
        List<String> etiquetas = new ArrayList<>(pendientes.size());
        for (Cancion cancion : pendientes) {
            etiquetas.add(cancion.getTitulo());
        }
        return new EstructuraVisual.Cola(ESTRUCTURA, etiquetas, historial().size());
    }
}
