package com.eia.reproductor.modelo;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa una cancion de la biblioteca del reproductor.
 *
 * <p>Todos los atributos son privados y solo se exponen mediante getters y setters que validan
 * los valores recibidos (encapsulamiento). El identificador {@code id} es inmutable: se genera
 * una unica vez al construir la cancion y es lo que permite distinguir dos canciones que tengan
 * exactamente el mismo titulo y artista.</p>
 *
 * <p>La clase implementa {@link Comparable} porque el modo de reproduccion alfabetico la almacena
 * dentro de un Arbol Binario de Busqueda, que necesita un criterio de orden total.</p>
 *
 * @see #compareTo(Cancion)
 */
public class Cancion implements Comparable<Cancion> {

    /** Calificacion minima que acepta el enunciado. */
    public static final int CALIFICACION_MIN = 0;

    /** Calificacion maxima que acepta el enunciado. */
    public static final int CALIFICACION_MAX = 100;

    /** Valor con el que nace una cancion a la que el usuario todavia no ha calificado. */
    public static final int CALIFICACION_POR_DEFECTO = 0;

    /** Texto que se muestra en la interfaz cuando un campo de texto viene vacio o nulo. */
    public static final String TEXTO_DESCONOCIDO = "Desconocido";

    private static final int SEGUNDOS_POR_MINUTO = 60;

    /**
     * Comparador de cadenas sensible al idioma espanol.
     *
     * <p>Con {@link Collator#PRIMARY} se ignoran tildes y mayusculas, de modo que "Angel",
     * "angel" y "Angel" con tilde quedan juntos en el orden alfabetico, que es lo que espera
     * un usuario hispanohablante y lo que un {@code String.compareTo()} (orden Unicode puro)
     * haria mal.</p>
     *
     * <p>Nota de concurrencia: {@link Collator} no es thread-safe. En esta aplicacion todas las
     * comparaciones ocurren en el hilo de la interfaz de JavaFX (o en el hilo principal durante
     * las pruebas), por lo que una unica instancia compartida es segura.</p>
     */
    private static final Collator COLLATOR_ESPANOL = crearCollatorEspanol();

    /**
     * Criterio de orden que usa el Arbol Binario de Busqueda del modo alfabetico.
     *
     * <p>Se expone como constante para que la logica de ordenamiento viva en un solo lugar y el
     * arbol no tenga que reimplementarla.</p>
     */
    public static final Comparator<Cancion> POR_TITULO = Cancion::compareTo;

    private final String id;
    private String titulo;
    private String artista;
    private String album;
    private int duracionSegundos;
    private String genero;
    private int anio;
    private int calificacion;
    private String rutaArchivo;
    private String uriSpotify;
    private String rutaPortada;
    private String urlPortadaRemota;
    private boolean favorita;
    private int vecesReproducida;

    /**
     * Crea una cancion nueva con un identificador generado automaticamente.
     *
     * @param titulo nombre de la cancion; es el unico campo obligatorio
     * @throws IllegalArgumentException si el titulo es nulo o esta en blanco
     */
    public Cancion(String titulo) {
        this(UUID.randomUUID().toString(), titulo);
    }

    /**
     * Reconstruye una cancion conservando un identificador ya existente.
     *
     * <p>Se usa al cargar la biblioteca desde {@code data/biblioteca.json}, donde el id debe
     * sobrevivir entre ejecuciones porque es el nombre del archivo de la caratula cacheada.</p>
     *
     * @param id     identificador previamente asignado
     * @param titulo nombre de la cancion
     * @throws IllegalArgumentException si el id o el titulo son nulos o estan en blanco
     */
    public Cancion(String id, String titulo) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El id de la cancion no puede estar vacio.");
        }
        this.id = id;
        setTitulo(titulo);
        this.artista = TEXTO_DESCONOCIDO;
        this.album = TEXTO_DESCONOCIDO;
        this.genero = TEXTO_DESCONOCIDO;
        this.calificacion = CALIFICACION_POR_DEFECTO;
    }

    private static Collator crearCollatorEspanol() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("es"));
        collator.setStrength(Collator.PRIMARY);
        return collator;
    }

    // ------------------------------------------------------------------
    // Getters y setters con validacion
    // ------------------------------------------------------------------

    /** @return el identificador unico e inmutable de la cancion */
    public String getId() {
        return id;
    }

    /** @return el nombre de la cancion */
    public String getTitulo() {
        return titulo;
    }

    /**
     * Cambia el nombre de la cancion.
     *
     * @param titulo nuevo nombre, no puede estar vacio
     * @throws IllegalArgumentException si el titulo es nulo o esta en blanco
     */
    public void setTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El titulo de la cancion no puede estar vacio.");
        }
        this.titulo = titulo.trim();
    }

    /** @return el nombre del artista, nunca nulo */
    public String getArtista() {
        return artista;
    }

    /** @param artista nuevo artista; si es nulo o vacio se guarda {@value #TEXTO_DESCONOCIDO} */
    public void setArtista(String artista) {
        this.artista = normalizarTexto(artista);
    }

    /** @return el nombre del album, nunca nulo */
    public String getAlbum() {
        return album;
    }

    /** @param album nuevo album; si es nulo o vacio se guarda {@value #TEXTO_DESCONOCIDO} */
    public void setAlbum(String album) {
        this.album = normalizarTexto(album);
    }

    /** @return la duracion total de la cancion en segundos */
    public int getDuracionSegundos() {
        return duracionSegundos;
    }

    /**
     * Define la duracion de la cancion.
     *
     * @param duracionSegundos duracion en segundos, no puede ser negativa
     * @throws IllegalArgumentException si la duracion es negativa
     */
    public void setDuracionSegundos(int duracionSegundos) {
        if (duracionSegundos < 0) {
            throw new IllegalArgumentException("La duracion no puede ser negativa: " + duracionSegundos);
        }
        this.duracionSegundos = duracionSegundos;
    }

    /** @return el genero musical, nunca nulo */
    public String getGenero() {
        return genero;
    }

    /** @param genero nuevo genero; si es nulo o vacio se guarda {@value #TEXTO_DESCONOCIDO} */
    public void setGenero(String genero) {
        this.genero = normalizarTexto(genero);
    }

    /** @return el anio de lanzamiento, o 0 si se desconoce */
    public int getAnio() {
        return anio;
    }

    /**
     * Define el anio de lanzamiento.
     *
     * @param anio anio de lanzamiento; 0 significa desconocido
     * @throws IllegalArgumentException si el anio es negativo
     */
    public void setAnio(int anio) {
        if (anio < 0) {
            throw new IllegalArgumentException("El anio no puede ser negativo: " + anio);
        }
        this.anio = anio;
    }

    /** @return la calificacion personal del usuario, entre 0 y 100 */
    public int getCalificacion() {
        return calificacion;
    }

    /**
     * Califica la cancion.
     *
     * @param calificacion valor entre {@value #CALIFICACION_MIN} y {@value #CALIFICACION_MAX}
     * @throws IllegalArgumentException si el valor cae fuera del rango permitido
     */
    public void setCalificacion(int calificacion) {
        if (calificacion < CALIFICACION_MIN || calificacion > CALIFICACION_MAX) {
            throw new IllegalArgumentException(
                    "La calificacion debe estar entre " + CALIFICACION_MIN + " y " + CALIFICACION_MAX
                            + ", se recibio: " + calificacion);
        }
        this.calificacion = calificacion;
    }

    /** @return la ruta al archivo de audio local, o {@code null} si la cancion es solo metadata */
    public String getRutaArchivo() {
        return rutaArchivo;
    }

    /** @param rutaArchivo ruta relativa al MP3/WAV dentro de {@code data/musica/}, admite {@code null} */
    public void setRutaArchivo(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }

    /** @return el identificador de la pista en Spotify, o {@code null} si no la tiene */
    public String getUriSpotify() {
        return uriSpotify;
    }

    /**
     * Asocia la cancion a una pista de Spotify.
     *
     * <p>El formato esperado es {@code spotify:track:<id>}. Es el dato que consulta la fuente de
     * audio de Spotify para decidir si sabe reproducir esta cancion; sin el, no hay forma de
     * deducir a que pista del catalogo corresponde.</p>
     *
     * @param uriSpotify URI de la pista, admite {@code null}
     */
    public void setUriSpotify(String uriSpotify) {
        this.uriSpotify = uriSpotify;
    }

    /** @return {@code true} si la cancion apunta a una pista de Spotify */
    public boolean tieneUriSpotify() {
        return uriSpotify != null && !uriSpotify.isBlank();
    }

    /** @return la ruta local a la caratula cacheada, o {@code null} si aun no se ha descargado */
    public String getRutaPortada() {
        return rutaPortada;
    }

    /** @param rutaPortada ruta relativa a la caratula dentro de {@code data/covers/}, admite {@code null} */
    public void setRutaPortada(String rutaPortada) {
        this.rutaPortada = rutaPortada;
    }

    /** @return la URL remota de la caratula, util para volver a descargarla si se borra la cache */
    public String getUrlPortadaRemota() {
        return urlPortadaRemota;
    }

    /** @param urlPortadaRemota URL de la caratula en alta resolucion, admite {@code null} */
    public void setUrlPortadaRemota(String urlPortadaRemota) {
        this.urlPortadaRemota = urlPortadaRemota;
    }

    /** @return {@code true} si el usuario marco la cancion como favorita */
    public boolean isFavorita() {
        return favorita;
    }

    /** @param favorita nuevo estado de favorita */
    public void setFavorita(boolean favorita) {
        this.favorita = favorita;
    }

    /** @return cuantas veces se ha reproducido la cancion */
    public int getVecesReproducida() {
        return vecesReproducida;
    }

    /**
     * Define el contador de reproducciones.
     *
     * @param vecesReproducida numero de reproducciones, no puede ser negativo
     * @throws IllegalArgumentException si el valor es negativo
     */
    public void setVecesReproducida(int vecesReproducida) {
        if (vecesReproducida < 0) {
            throw new IllegalArgumentException(
                    "El contador de reproducciones no puede ser negativo: " + vecesReproducida);
        }
        this.vecesReproducida = vecesReproducida;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Suma uno al contador de reproducciones. Lo invoca el servicio de audio al iniciar una pista. */
    public void registrarReproduccion() {
        vecesReproducida++;
    }

    /** Invierte el estado de favorita. */
    public void alternarFavorita() {
        favorita = !favorita;
    }

    /**
     * Formatea la duracion para mostrarla en la interfaz.
     *
     * @return la duracion en formato {@code m:ss}, por ejemplo {@code "5:55"}
     */
    public String duracionFormateada() {
        int minutos = duracionSegundos / SEGUNDOS_POR_MINUTO;
        int segundos = duracionSegundos % SEGUNDOS_POR_MINUTO;
        return String.format("%d:%02d", minutos, segundos);
    }

    /** @return {@code true} si la cancion tiene un archivo de audio asociado que se puede reproducir de verdad */
    public boolean tieneAudioReal() {
        return rutaArchivo != null && !rutaArchivo.isBlank();
    }

    private static String normalizarTexto(String valor) {
        return (valor == null || valor.isBlank()) ? TEXTO_DESCONOCIDO : valor.trim();
    }

    // ------------------------------------------------------------------
    // Orden, igualdad y representacion
    // ------------------------------------------------------------------

    /**
     * Ordena las canciones alfabeticamente por titulo segun las reglas del idioma espanol.
     *
     * <p>El desempate no es opcional: el Arbol Binario de Busqueda del modo alfabetico descarta
     * cualquier elemento que compare igual a uno ya insertado. Si dos canciones distintas tuvieran
     * el mismo titulo y este metodo devolviera 0, el arbol perderia una de las dos. Por eso, ante
     * titulos iguales se desempata por artista y, si tambien coincide, por el id, que es unico
     * por construccion y garantiza que el resultado nunca sea 0 para canciones diferentes.</p>
     *
     * @param otra la cancion contra la cual comparar
     * @return negativo, cero o positivo segun esta cancion vaya antes, igual o despues que {@code otra}
     */
    @Override
    public int compareTo(Cancion otra) {
        int porTitulo = COLLATOR_ESPANOL.compare(this.titulo, otra.titulo);
        if (porTitulo != 0) {
            return porTitulo;
        }
        int porArtista = COLLATOR_ESPANOL.compare(this.artista, otra.artista);
        if (porArtista != 0) {
            return porArtista;
        }
        return this.id.compareTo(otra.id);
    }

    /**
     * Dos canciones son la misma si comparten identificador.
     *
     * <p>Se compara por id y no por titulo para que la biblioteca admita canciones homonimas
     * (versiones en vivo, covers, remasterizaciones) sin que una desplace a la otra.</p>
     */
    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) {
            return true;
        }
        if (!(objeto instanceof Cancion otra)) {
            return false;
        }
        return id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return titulo + " - " + artista + " (" + duracionFormateada() + ")";
    }
}
