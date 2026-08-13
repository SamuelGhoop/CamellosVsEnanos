package com.eia.reproductor.modos;

import com.eia.reproductor.estructuras.ColaSimple;
import com.eia.reproductor.modelo.Cancion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Modo 2: reproduccion por orden de llegada sobre una {@link ColaSimple}.
 *
 * <p>Las canciones se encolan en el mismo orden en que fueron agregadas a la biblioteca y se
 * reproducen respetando el principio FIFO. Cada {@link #siguiente()} hace un {@code desencolar()}
 * real: la cancion <b>sale de la cola</b> y no se puede volver a ella.</p>
 *
 * <p><b>Por que una cola simple.</b> El enunciado describe exactamente el comportamiento de una
 * cola: se atiende primero al que llego primero, no se puede retroceder y lo ya atendido se va. La
 * estructura no es una decoracion, es la que <i>impone</i> esa regla: como el nodo de la cola solo
 * apunta hacia adelante, retroceder no es que este prohibido por una validacion, es que
 * fisicamente no hay por donde volver. Por eso {@link #permiteAnterior()} devuelve {@code false} y
 * la interfaz deshabilita el boton "Anterior".</p>
 *
 * <p>Cuando la cola se agota, {@link #hayMas()} pasa a {@code false} y la unica forma de volver a
 * escuchar algo es recargarla desde la biblioteca con {@link #cargar(Iterable)}.</p>
 */
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
        // desencolar. Cuesta O(n) y deja intacta la pureza FIFO de la estructura.
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
}
