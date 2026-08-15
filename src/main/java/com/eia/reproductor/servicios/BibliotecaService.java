package com.eia.reproductor.servicios;

import com.eia.reproductor.estructuras.ListaCircularDoble;
import com.eia.reproductor.modelo.Cancion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Fuente unica de verdad de la coleccion de canciones.
 *
 * <p>Toda alta, baja o edicion pasa por aqui. Los modos de reproduccion <b>no</b> guardan la
 * coleccion: guardan una vista suya, construida con {@code cargar()} cada vez que se cambia de
 * modo. Asi solo hay un sitio donde la biblioteca puede quedar mal, en vez de cuatro copias que
 * se desincronizan entre si.</p>
 *
 * <p><b>Por que la biblioteca maestra es una {@link ListaCircularDoble} y no un arreglo.</b>
 * Porque el orden de insercion <i>es informacion del dominio</i>: el modo de orden de llegada
 * depende de el, asi que la estructura maestra tiene que conservarlo intacto. Ademas agregar
 * cuesta O(1) y eliminar solo requiere desenlazar. Y de paso, el proyecto usa sus propias
 * estructuras tambien fuera de los modos.</p>
 *
 * <p>Los cambios se avisan a los {@link ObservadorBiblioteca} registrados y se persisten en disco
 * de inmediato, tal como pide el enunciado.</p>
 */
public class BibliotecaService {

    private final ListaCircularDoble<Cancion> maestra = new ListaCircularDoble<>();
    private final List<ObservadorBiblioteca> observadores = new ArrayList<>();
    private final PersistenciaService persistencia;

    /** Crea la biblioteca con la persistencia por defecto ({@code data/biblioteca.json}). */
    public BibliotecaService() {
        this(new PersistenciaService());
    }

    /**
     * Crea la biblioteca con un servicio de persistencia concreto.
     *
     * @param persistencia servicio encargado de leer y escribir el archivo
     */
    public BibliotecaService(PersistenciaService persistencia) {
        this.persistencia = Objects.requireNonNull(persistencia,
                "El servicio de persistencia no puede ser nulo.");
    }

    // ------------------------------------------------------------------
    // Observadores
    // ------------------------------------------------------------------

    /**
     * Registra a alguien interesado en los cambios de la biblioteca.
     *
     * @param observador el interesado; se ignora si ya estaba registrado
     */
    public void registrarObservador(ObservadorBiblioteca observador) {
        Objects.requireNonNull(observador, "El observador no puede ser nulo.");
        if (!observadores.contains(observador)) {
            observadores.add(observador);
        }
    }

    /**
     * Da de baja a un observador.
     *
     * @param observador el observador a retirar
     */
    public void quitarObservador(ObservadorBiblioteca observador) {
        observadores.remove(observador);
    }

    // ------------------------------------------------------------------
    // Altas, bajas y ediciones
    // ------------------------------------------------------------------

    /**
     * Agrega una cancion a la biblioteca.
     *
     * <p><b>Complejidad:</b> O(n) por la comprobacion de duplicados; el enlace en si es O(1).</p>
     *
     * @param cancion cancion a agregar
     * @return {@code true} si se agrego, {@code false} si ya estaba
     * @throws NullPointerException si la cancion es {@code null}
     */
    public boolean agregar(Cancion cancion) {
        Objects.requireNonNull(cancion, "La cancion no puede ser nula.");
        if (maestra.buscar(cancion)) {
            return false;
        }
        maestra.agregar(cancion);
        for (ObservadorBiblioteca observador : copiaDeObservadores()) {
            observador.cancionAgregada(cancion);
        }
        guardar();
        return true;
    }

    /**
     * Elimina una cancion de la biblioteca.
     *
     * <p><b>Complejidad:</b> O(n).</p>
     *
     * @param cancion cancion a eliminar
     * @return {@code true} si se elimino, {@code false} si no estaba
     */
    public boolean eliminar(Cancion cancion) {
        if (cancion == null || !maestra.eliminar(cancion)) {
            return false;
        }
        for (ObservadorBiblioteca observador : copiaDeObservadores()) {
            observador.cancionEliminada(cancion);
        }
        guardar();
        return true;
    }

    /**
     * Modifica una cancion de forma segura para todas las estructuras.
     *
     * <p>Los cambios se aplican dentro de una ventana delimitada por
     * {@link ObservadorBiblioteca#antesDeEditar(Cancion)} y
     * {@link ObservadorBiblioteca#despuesDeEditar(Cancion)}. Esto no es ceremonia de mas: el modo
     * alfabetico guarda las canciones en un arbol ordenado por titulo, y cambiarle el titulo a una
     * cancion que sigue dentro del arbol la volveria inencontrable. Retirandola antes y
     * reinsertandola despues, el arbol la recoloca sola en su nueva posicion alfabetica.</p>
     *
     * <p>Ejemplo de uso: {@code biblioteca.editar(cancion, c -> c.setTitulo("Nuevo titulo"));}</p>
     *
     * @param cancion cancion a modificar, debe pertenecer a la biblioteca
     * @param cambios operacion que aplica las modificaciones sobre la cancion
     * @return {@code true} si la cancion estaba en la biblioteca y se edito
     */
    public boolean editar(Cancion cancion, Consumer<Cancion> cambios) {
        Objects.requireNonNull(cambios, "La operacion de edicion no puede ser nula.");
        if (cancion == null || !maestra.buscar(cancion)) {
            return false;
        }

        List<ObservadorBiblioteca> interesados = copiaDeObservadores();
        for (ObservadorBiblioteca observador : interesados) {
            observador.antesDeEditar(cancion);
        }

        cambios.accept(cancion);

        for (ObservadorBiblioteca observador : interesados) {
            observador.despuesDeEditar(cancion);
        }
        guardar();
        return true;
    }

    /**
     * Cambia la calificacion personal de una cancion.
     *
     * @param cancion      cancion a calificar
     * @param calificacion valor entre 0 y 100
     * @return {@code true} si la cancion estaba en la biblioteca
     * @throws IllegalArgumentException si la calificacion cae fuera de rango
     */
    public boolean calificar(Cancion cancion, int calificacion) {
        return editar(cancion, objetivo -> objetivo.setCalificacion(calificacion));
    }

    /**
     * Marca o desmarca una cancion como favorita.
     *
     * @param cancion cancion a alternar
     * @return {@code true} si la cancion estaba en la biblioteca
     */
    public boolean alternarFavorita(Cancion cancion) {
        return editar(cancion, Cancion::alternarFavorita);
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    /**
     * Devuelve la biblioteca completa en orden de insercion.
     *
     * <p>Es una copia: modificarla no altera la biblioteca. Sirve para llenar la tabla de la
     * interfaz y para que los modos construyan su estructura.</p>
     *
     * <p><b>Complejidad:</b> O(n).</p>
     *
     * @return todas las canciones, de la mas antigua a la mas reciente
     */
    public List<Cancion> todas() {
        List<Cancion> copia = new ArrayList<>(maestra.tamanio());
        for (Cancion cancion : maestra) {
            copia.add(cancion);
        }
        return copia;
    }

    /**
     * Busca canciones cuyo titulo, artista o album contengan el texto indicado.
     *
     * <p>La comparacion ignora mayusculas y tildes, para que "angel" encuentre "Ángel".</p>
     *
     * <p><b>Complejidad:</b> O(n).</p>
     *
     * @param texto texto a buscar; si viene vacio se devuelve la biblioteca completa
     * @return las canciones que coinciden
     */
    public List<Cancion> buscar(String texto) {
        if (texto == null || texto.isBlank()) {
            return todas();
        }
        String aguja = Texto.plano(texto);
        return filtrar(cancion ->
                Texto.plano(cancion.getTitulo()).contains(aguja)
                        || Texto.plano(cancion.getArtista()).contains(aguja)
                        || Texto.plano(cancion.getAlbum()).contains(aguja));
    }

    /**
     * Devuelve las canciones que cumplen una condicion.
     *
     * <p>Es la base de los filtros por artista, genero, album o favoritas.</p>
     *
     * <p><b>Complejidad:</b> O(n).</p>
     *
     * @param condicion condicion que deben cumplir las canciones
     * @return las canciones que la cumplen, en orden de insercion
     */
    public List<Cancion> filtrar(Predicate<Cancion> condicion) {
        Objects.requireNonNull(condicion, "La condicion no puede ser nula.");
        List<Cancion> resultado = new ArrayList<>();
        for (Cancion cancion : maestra) {
            if (condicion.test(cancion)) {
                resultado.add(cancion);
            }
        }
        return resultado;
    }

    /**
     * Busca una cancion por su identificador.
     *
     * @param id identificador buscado
     * @return la cancion, o {@code null} si no esta en la biblioteca
     */
    public Cancion porId(String id) {
        if (id == null) {
            return null;
        }
        for (Cancion cancion : maestra) {
            if (id.equals(cancion.getId())) {
                return cancion;
            }
        }
        return null;
    }

    /**
     * @return cantidad de canciones en la biblioteca
     *         <p><b>Complejidad:</b> O(1).</p>
     */
    public int tamanio() {
        return maestra.tamanio();
    }

    /**
     * @return {@code true} si la biblioteca no tiene canciones
     */
    public boolean estaVacia() {
        return maestra.estaVacia();
    }

    /**
     * @return {@code true} si la cancion pertenece a la biblioteca
     */
    public boolean contiene(Cancion cancion) {
        return cancion != null && maestra.buscar(cancion);
    }

    // ------------------------------------------------------------------
    // Persistencia
    // ------------------------------------------------------------------

    /**
     * Reemplaza la biblioteca por el contenido del archivo en disco.
     *
     * <p>Si el archivo no existe o esta corrupto, la biblioteca queda vacia y el aviso
     * correspondiente se puede consultar con {@link #ultimoAviso()}. La aplicacion arranca
     * igual.</p>
     *
     * @return cantidad de canciones cargadas
     */
    public int cargarDesdeDisco() {
        maestra.limpiar();
        for (Cancion cancion : persistencia.cargar()) {
            maestra.agregar(cancion);
        }
        for (ObservadorBiblioteca observador : copiaDeObservadores()) {
            observador.bibliotecaRecargada(todas());
        }
        return maestra.tamanio();
    }

    /**
     * Escribe la biblioteca en disco.
     *
     * <p>Se invoca sola tras cada alta, baja o edicion. Tambien conviene llamarla al cerrar la
     * aplicacion.</p>
     *
     * @return {@code true} si se guardo correctamente
     */
    public boolean guardar() {
        return persistencia.guardar(todas());
    }

    /**
     * @return el aviso de la ultima operacion de disco, si hubo algun problema
     */
    public Optional<String> ultimoAviso() {
        return persistencia.ultimoAviso();
    }

    // ------------------------------------------------------------------
    // Apoyo interno
    // ------------------------------------------------------------------

    /**
     * Devuelve una copia de la lista de observadores.
     *
     * <p>Se notifica sobre la copia para que un observador que se dé de baja a si mismo mientras
     * atiende el aviso no rompa el recorrido en curso.</p>
     */
    private List<ObservadorBiblioteca> copiaDeObservadores() {
        return new ArrayList<>(observadores);
    }
}
