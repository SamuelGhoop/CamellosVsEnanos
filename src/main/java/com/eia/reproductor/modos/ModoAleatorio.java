package com.eia.reproductor.modos;

import com.eia.reproductor.estructuras.ListaCircularDoble;
import com.eia.reproductor.modelo.Cancion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Modo 1: reproduccion aleatoria sobre una {@link ListaCircularDoble}.
 *
 * <p>Al cargarse, la coleccion se baraja con Fisher-Yates y queda encadenada en un anillo. A partir
 * de ahi el modo navega con un cursor que avanza y retrocede en O(1).</p>
 *
 * <p><b>Por que una lista circular doble.</b> El enunciado pide moverse en las dos direcciones
 * indefinidamente y que despues de la ultima cancion venga la primera. Con esta estructura eso sale
 * gratis: como el ultimo nodo apunta al primero, el codigo que avanza nunca necesita preguntar "y
 * si llegue al final?". No hay ningun caso especial que programar, la vuelta la da la estructura.
 * Y como los enlaces van en ambos sentidos, retroceder cuesta lo mismo que avanzar.</p>
 */
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

    /**
     * Crea el modo con una fuente de aleatoriedad concreta.
     *
     * <p>Existe para que las pruebas puedan sembrar el generador y obtener barajados
     * reproducibles.</p>
     *
     * @param aleatorio generador de numeros aleatorios, no puede ser {@code null}
     */
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

    /**
     * Vuelve a barajar la coleccion sin interrumpir lo que suena.
     *
     * <p>Es la accion del boton "Volver a mezclar". La cancion en curso se mantiene: como
     * {@code mezclar()} reordena los nodos en lugar de mover los datos entre ellos, el cursor sigue
     * parado sobre la misma cancion y lo que cambia es lo que viene despues.</p>
     *
     * <p><b>Complejidad:</b> O(n).</p>
     */
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
}
