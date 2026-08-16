package com.eia.reproductor.modelo;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Representa una cancion de la biblioteca del reproductor. */
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

    /** Comparador de cadenas sensible al idioma espanol. */
    private static final Collator COLLATOR_ESPANOL = crearCollatorEspanol();

    /** Criterio de orden que usa el Arbol Binario de Busqueda del modo alfabetico. */
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

    /** Crea una cancion nueva con un identificador generado automaticamente. */
    public Cancion(String titulo) {
        this(UUID.randomUUID().toString(), titulo);
    }

    /** Reconstruye una cancion conservando un identificador ya existente. */
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

    // --- Getters y setters con validacion ---

    /** @return el identificador unico e inmutable de la cancion */
    public String getId() {
        return id;
    }

    /** @return el nombre de la cancion */
    public String getTitulo() {
        return titulo;
    }

    /** Cambia el nombre de la cancion. */
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

    public void setArtista(String artista) {
        this.artista = normalizarTexto(artista);
    }

    /** @return el nombre del album, nunca nulo */
    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = normalizarTexto(album);
    }

    /** @return la duracion total de la cancion en segundos */
    public int getDuracionSegundos() {
        return duracionSegundos;
    }

    /** Define la duracion de la cancion. */
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

    public void setGenero(String genero) {
        this.genero = normalizarTexto(genero);
    }

    /** @return el anio de lanzamiento, o 0 si se desconoce */
    public int getAnio() {
        return anio;
    }

    /** Define el anio de lanzamiento. */
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

    /** Califica la cancion. */
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

    public void setRutaArchivo(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }

    /** @return el identificador de la pista en Spotify, o {@code null} si no la tiene */
    public String getUriSpotify() {
        return uriSpotify;
    }

    /** Asocia la cancion a una pista de Spotify. */
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

    public void setRutaPortada(String rutaPortada) {
        this.rutaPortada = rutaPortada;
    }

    /** @return la URL remota de la caratula, util para volver a descargarla si se borra la cache */
    public String getUrlPortadaRemota() {
        return urlPortadaRemota;
    }

    public void setUrlPortadaRemota(String urlPortadaRemota) {
        this.urlPortadaRemota = urlPortadaRemota;
    }

    /** @return {@code true} si el usuario marco la cancion como favorita */
    public boolean isFavorita() {
        return favorita;
    }

    public void setFavorita(boolean favorita) {
        this.favorita = favorita;
    }

    /** @return cuantas veces se ha reproducido la cancion */
    public int getVecesReproducida() {
        return vecesReproducida;
    }

    /** Define el contador de reproducciones. */
    public void setVecesReproducida(int vecesReproducida) {
        if (vecesReproducida < 0) {
            throw new IllegalArgumentException(
                    "El contador de reproducciones no puede ser negativo: " + vecesReproducida);
        }
        this.vecesReproducida = vecesReproducida;
    }

    // --- Utilidades ---

    /** Suma uno al contador de reproducciones. */
    public void registrarReproduccion() {
        vecesReproducida++;
    }

    /** Invierte el estado de favorita. */
    public void alternarFavorita() {
        favorita = !favorita;
    }

    /** Formatea la duracion para mostrarla en la interfaz. */
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

    // --- Orden, igualdad y representacion ---

    /** Ordena las canciones alfabeticamente por titulo segun las reglas del idioma espanol. */
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

    /** Dos canciones son la misma si comparten identificador. */
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
