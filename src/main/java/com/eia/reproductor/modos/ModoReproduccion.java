package com.eia.reproductor.modos;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.EstructuraVisual;

import java.util.List;

/**
 * Contrato que cumple todo modo de reproduccion.
 *
 * <p>Es la pieza que hace posible el polimorfismo del proyecto: el controlador guarda una
 * referencia de este tipo y llama {@code siguiente()} o {@code anterior()} sin saber nunca si por
 * debajo hay una lista circular, una cola o un arbol. Cambiar de modo es reasignar la referencia,
 * no un {@code if} ni un {@code switch} sobre tipos concretos.</p>
 *
 * <p>Las tres implementaciones heredan de {@link ModoBase}, que resuelve la parte comun (cancion
 * actual, historial y validaciones) y deja a cada modo solo la decision de <i>cual</i> es la
 * cancion siguiente o anterior segun su estructura.</p>
 *
 * @see ModoBase
 * @see ModoAleatorio
 * @see ModoOrdenLlegada
 * @see ModoAlfabetico
 */
public interface ModoReproduccion {

    /**
     * Vuelca la biblioteca dentro de la estructura del modo.
     *
     * <p>Se invoca cada vez que el usuario cambia de modo: la estructura se reconstruye desde cero
     * a partir de la biblioteca, que es la unica fuente de verdad.</p>
     *
     * <p>Al terminar, {@link #actual()} devuelve {@code null}: la carga deja el modo listo pero sin
     * nada sonando todavia. La primera llamada a {@link #siguiente()} arranca la reproduccion.</p>
     *
     * @param canciones canciones a cargar, en el orden en que estan en la biblioteca
     */
    void cargar(Iterable<Cancion> canciones);

    /**
     * @return la cancion que se esta reproduciendo, o {@code null} si no ha empezado la reproduccion
     */
    Cancion actual();

    /**
     * Avanza a la siguiente cancion segun la logica del modo.
     *
     * @return la cancion que pasa a reproducirse
     * @throws java.util.NoSuchElementException si el modo no tiene canciones disponibles
     */
    Cancion siguiente();

    /**
     * Retrocede a la cancion anterior segun la logica del modo.
     *
     * @return la cancion que pasa a reproducirse
     * @throws UnsupportedOperationException    si el modo no permite retroceder
     * @throws java.util.NoSuchElementException si el modo no tiene canciones disponibles
     */
    Cancion anterior();

    /**
     * Indica si el modo admite retroceder.
     *
     * <p>La interfaz grafica consulta este metodo para habilitar o deshabilitar el boton
     * "Anterior", en vez de preguntar de que clase es el modo.</p>
     *
     * @return {@code true} si {@link #anterior()} es una operacion valida
     */
    boolean permiteAnterior();

    /**
     * @return {@code true} si todavia queda algo por reproducir
     */
    boolean hayMas();

    /**
     * Incorpora una cancion recien agregada a la biblioteca.
     *
     * @param cancion cancion a incorporar
     */
    void agregar(Cancion cancion);

    /**
     * Saca de la estructura una cancion eliminada de la biblioteca.
     *
     * <p>Si la cancion eliminada era la que sonaba, el modo se reposiciona solo para que la
     * navegacion siga funcionando.</p>
     *
     * @param cancion cancion a retirar
     */
    void eliminar(Cancion cancion);

    /**
     * Avisa que una cancion ya presente esta a punto de cambiar de datos.
     *
     * <p>Solo hacen algo los modos cuya estructura ubica los elementos <i>segun el contenido de la
     * cancion</i>. Si se le cambiara el titulo a una cancion mientras esta dentro de un arbol
     * ordenado por titulo, el nodo quedaria colocado segun un valor que ya no existe y la busqueda
     * jamas volveria a dar con el.</p>
     *
     * @param cancion la cancion que va a cambiar, todavia con sus datos viejos
     */
    void prepararEdicion(Cancion cancion);

    /**
     * Avisa que una cancion termino de cambiar de datos y hay que reubicarla.
     *
     * @param cancion la cancion ya modificada
     */
    void confirmarEdicion(Cancion cancion);

    /**
     * @return el nombre del modo, para mostrarlo en la interfaz
     */
    String nombre();

    /**
     * @return el nombre de la estructura de datos que usa el modo, para mostrarlo en la interfaz
     */
    String estructuraUsada();

    /**
     * Devuelve el orden de reproduccion tal como lo ve el modo, <b>solo para mostrarlo</b>.
     *
     * <p>Permite que la interfaz pinte la lista de reproduccion sin saber que estructura hay
     * detras. La navegacion nunca debe apoyarse en esta lista ni en sus indices.</p>
     *
     * @return las canciones en el orden del modo; lista vacia si no hay ninguna
     */
    List<Cancion> listaReproduccion();

    /**
     * Devuelve las canciones ya reproducidas, de la mas antigua a la mas reciente.
     *
     * <p>Lo implementa {@code ModoBase} una sola vez para los tres modos, con una
     * {@code ColaSimple} acotada: cuando se pasa del tope, descarta la mas antigua en O(1). Es el
     * segundo uso de esa estructura en el proyecto, y el que mejor explica para que sirve una
     * cola mas alla del modo de orden de llegada.</p>
     *
     * <p>Cada modo lleva el suyo, no hay uno global: cambiar de modo es cambiar de forma de
     * recorrer, y mezclar los recorridos en una sola lista confundiria mas de lo que ayuda.</p>
     *
     * @return copia del historial en orden cronologico; lista vacia si no se ha reproducido nada
     */
    List<Cancion> historial();

    /**
     * Describe la estructura de datos que este modo tiene cargada, para poder dibujarla.
     *
     * <p>Cada modo la construye a partir de su propia estructura. Quien la dibuje no pregunta de
     * que clase es el modo: recibe la descripcion y la pinta, que es el mismo polimorfismo que ya
     * usa el resto de la interfaz.</p>
     *
     * @return la estructura tal como esta ahora mismo
     */
    EstructuraVisual estructuraVisual();

    /**
     * Devuelve la reproduccion al comienzo de lo que el modo tenga cargado.
     */
    void reiniciar();
}
