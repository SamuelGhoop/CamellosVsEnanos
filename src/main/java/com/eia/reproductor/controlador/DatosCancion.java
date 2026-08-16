package com.eia.reproductor.controlador;

import com.eia.reproductor.modelo.Cancion;

/** Valores que el usuario dejo en el formulario de agregar o editar. */
public record DatosCancion(String titulo, String artista, String album, String genero,
                           int anio, int duracionSegundos, int calificacion, boolean favorita,
                           String rutaArchivo, String urlPortadaRemota, String uriSpotify) {
    /** Construye una cancion nueva con estos valores. */
    public Cancion crearCancion() {
        Cancion cancion = new Cancion(titulo);
        aplicarA(cancion);
        return cancion;
    }

    /** Vuelca estos valores sobre una cancion existente. */
    public void aplicarA(Cancion cancion) {
        cancion.setTitulo(titulo);
        cancion.setArtista(artista);
        cancion.setAlbum(album);
        cancion.setGenero(genero);
        cancion.setAnio(anio);
        cancion.setDuracionSegundos(duracionSegundos);
        cancion.setCalificacion(calificacion);
        cancion.setFavorita(favorita);
        cancion.setRutaArchivo(rutaArchivo);
        cancion.setUrlPortadaRemota(urlPortadaRemota);
        cancion.setUriSpotify(uriSpotify);
    }

    /** Toma los valores actuales de una cancion, para precargar el formulario de edicion. */
    public static DatosCancion de(Cancion cancion) {
        return new DatosCancion(
                cancion.getTitulo(),
                cancion.getArtista(),
                cancion.getAlbum(),
                cancion.getGenero(),
                cancion.getAnio(),
                cancion.getDuracionSegundos(),
                cancion.getCalificacion(),
                cancion.isFavorita(),
                cancion.getRutaArchivo(),
                cancion.getUrlPortadaRemota(),
                cancion.getUriSpotify());
    }
}
