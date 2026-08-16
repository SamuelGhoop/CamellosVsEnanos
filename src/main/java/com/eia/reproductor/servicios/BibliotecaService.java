package com.eia.reproductor.servicios;

import com.eia.reproductor.estructuras.ListaCircularDoble;
import com.eia.reproductor.modelo.Cancion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Fuente unica de verdad de la coleccion de canciones. */
public class BibliotecaService {
    private final ListaCircularDoble<Cancion> maestra = new ListaCircularDoble<>();
    private final List<ObservadorBiblioteca> observadores = new ArrayList<>();
    private final PersistenciaService persistencia;

    /** Crea la biblioteca con la persistencia por defecto ({@code data/biblioteca.json}). */
    public BibliotecaService() {
        this(new PersistenciaService());
    }

    /** Crea la biblioteca con un servicio de persistencia concreto. */
    public BibliotecaService(PersistenciaService persistencia) {
        this.persistencia = Objects.requireNonNull(persistencia,
                "El servicio de persistencia no puede ser nulo.");
    }

    // --- Observadores ---

    /** Registra a alguien interesado en los cambios de la biblioteca. */
    public void registrarObservador(ObservadorBiblioteca observador) {
        Objects.requireNonNull(observador, "El observador no puede ser nulo.");
        if (!observadores.contains(observador)) {
            observadores.add(observador);
        }
    }

    /** Da de baja a un observador. */
    public void quitarObservador(ObservadorBiblioteca observador) {
        observadores.remove(observador);
    }

    // --- Altas, bajas y ediciones ---

    /** Agrega una cancion a la biblioteca. O(n). */
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

    /** Elimina una cancion de la biblioteca. O(n). */
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

    /** Modifica una cancion de forma segura para todas las estructuras. */
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

    /** Cambia la calificacion personal de una cancion. */
    public boolean calificar(Cancion cancion, int calificacion) {
        return editar(cancion, objetivo -> objetivo.setCalificacion(calificacion));
    }

    /** Marca o desmarca una cancion como favorita. */
    public boolean alternarFavorita(Cancion cancion) {
        return editar(cancion, Cancion::alternarFavorita);
    }

    // --- Consultas ---

    /** Devuelve la biblioteca completa en orden de insercion. O(n). */
    public List<Cancion> todas() {
        List<Cancion> copia = new ArrayList<>(maestra.tamanio());
        for (Cancion cancion : maestra) {
            copia.add(cancion);
        }
        return copia;
    }

    /** Busca canciones cuyo titulo, artista o album contengan el texto indicado. O(n). */
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

    /** Devuelve las canciones que cumplen una condicion. O(n). */
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

    /** Busca una cancion por su identificador. */
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

    /** @return cantidad de canciones en la biblioteca. O(1). */
    public int tamanio() {
        return maestra.tamanio();
    }

    /** @return {@code true} si la biblioteca no tiene canciones */
    public boolean estaVacia() {
        return maestra.estaVacia();
    }

    /** @return {@code true} si la cancion pertenece a la biblioteca */
    public boolean contiene(Cancion cancion) {
        return cancion != null && maestra.buscar(cancion);
    }

    // --- Persistencia ---

    /** Reemplaza la biblioteca por el contenido del archivo en disco. */
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

    /** Escribe la biblioteca en disco. */
    public boolean guardar() {
        return persistencia.guardar(todas());
    }

    /** @return el aviso de la ultima operacion de disco, si hubo algun problema */
    public Optional<String> ultimoAviso() {
        return persistencia.ultimoAviso();
    }

    // --- Apoyo interno ---

    /** Devuelve una copia de la lista de observadores. */
    private List<ObservadorBiblioteca> copiaDeObservadores() {
        return new ArrayList<>(observadores);
    }
}
