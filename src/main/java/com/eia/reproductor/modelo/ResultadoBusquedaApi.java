package com.eia.reproductor.modelo;

/**
 * Una cancion tal como la devuelve una API de metadata, antes de entrar a la biblioteca.
 *
 * <p>Es deliberadamente distinto de {@link Cancion}: aqui no hay identificador, ni calificacion,
 * ni archivo de audio, porque nada de eso lo da una API. Es un resultado de busqueda que el
 * usuario todavia puede descartar. Solo cuando elige uno y pulsa Guardar se construye una
 * {@link Cancion} de verdad.</p>
 *
 * @param titulo             nombre de la cancion
 * @param artista            interprete
 * @param album              album al que pertenece
 * @param duracionSegundos   duracion segun la API, 0 si no la informa
 * @param genero             genero musical, puede venir vacio
 * @param anio               anio de lanzamiento, 0 si no se conoce
 * @param urlPortadaGrande   caratula en alta resolucion, puede ser {@code null}
 * @param urlPortadaMiniatura caratula pequenia para la lista de resultados, puede ser {@code null}
 * @param fuente             que API entrego el resultado, para mostrarlo en la interfaz
 */
public record ResultadoBusquedaApi(String titulo, String artista, String album,
                                   int duracionSegundos, String genero, int anio,
                                   String urlPortadaGrande, String urlPortadaMiniatura,
                                   String fuente) {

    /** Nombre de la API principal. */
    public static final String FUENTE_ITUNES = "iTunes";

    /** Nombre de la API de respaldo. */
    public static final String FUENTE_MUSICBRAINZ = "MusicBrainz";

    private static final int SEGUNDOS_POR_MINUTO = 60;

    /**
     * @return la duracion en formato {@code m:ss}, o {@code "-"} si la API no la informo
     */
    public String duracionFormateada() {
        if (duracionSegundos <= 0) {
            return "-";
        }
        return String.format("%d:%02d", duracionSegundos / SEGUNDOS_POR_MINUTO,
                duracionSegundos % SEGUNDOS_POR_MINUTO);
    }

    /**
     * @return una linea con album, anio y duracion, para la lista de resultados
     */
    public String detalle() {
        StringBuilder texto = new StringBuilder();
        texto.append(album == null || album.isBlank() ? "Álbum desconocido" : album);
        if (anio > 0) {
            texto.append("  •  ").append(anio);
        }
        if (duracionSegundos > 0) {
            texto.append("  •  ").append(duracionFormateada());
        }
        texto.append("  •  ").append(fuente);
        return texto.toString();
    }

    /**
     * Convierte este resultado en una cancion nueva de la biblioteca.
     *
     * <p>La calificacion queda en su valor por defecto: es lo unico que la API no puede saber.</p>
     *
     * @return una cancion lista para agregarse a la biblioteca
     */
    public Cancion aCancion() {
        Cancion cancion = new Cancion(titulo);
        cancion.setArtista(artista);
        cancion.setAlbum(album);
        cancion.setGenero(genero);
        cancion.setAnio(Math.max(0, anio));
        cancion.setDuracionSegundos(Math.max(0, duracionSegundos));
        cancion.setUrlPortadaRemota(urlPortadaGrande);
        return cancion;
    }
}
