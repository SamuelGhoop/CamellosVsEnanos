package com.eia.reproductor.modelo;

/** Una cancion tal como la devuelve una API de metadata, antes de entrar a la biblioteca. */
public record ResultadoBusquedaApi(String titulo, String artista, String album,
                                   int duracionSegundos, String genero, int anio,
                                   String urlPortadaGrande, String urlPortadaMiniatura,
                                   String fuente) {
    /** Nombre de la API principal. */
    public static final String FUENTE_ITUNES = "iTunes";

    /** Nombre de la API de respaldo. */
    public static final String FUENTE_MUSICBRAINZ = "MusicBrainz";

    private static final int SEGUNDOS_POR_MINUTO = 60;

    /** @return la duracion en formato {@code m:ss}, o {@code "-"} si la API no la informo */
    public String duracionFormateada() {
        if (duracionSegundos <= 0) {
            return "-";
        }
        return String.format("%d:%02d", duracionSegundos / SEGUNDOS_POR_MINUTO,
                duracionSegundos % SEGUNDOS_POR_MINUTO);
    }

    /** @return una linea con album, anio y duracion, para la lista de resultados */
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

    /** Convierte este resultado en una cancion nueva de la biblioteca. */
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
