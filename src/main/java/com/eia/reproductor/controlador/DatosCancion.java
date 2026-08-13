package com.eia.reproductor.controlador;

import com.eia.reproductor.modelo.Cancion;

/**
 * Valores que el usuario dejo en el formulario de agregar o editar.
 *
 * <p>Es un simple transportador de datos entre el dialogo y el controlador principal. Se usa un
 * objeto intermedio en vez de dejar que el dialogo modifique la cancion directamente porque
 * editar tiene que pasar por {@code BibliotecaService.editar(...)}, que es quien abre la ventana
 * antes/despues que el modo alfabetico necesita para recolocar la cancion en su arbol.</p>
 *
 * @param titulo            nombre de la cancion, obligatorio
 * @param artista           interprete
 * @param album             album al que pertenece
 * @param genero            genero musical
 * @param anio              anio de lanzamiento, 0 si se desconoce
 * @param duracionSegundos  duracion total en segundos
 * @param calificacion      calificacion personal, entre 0 y 100
 * @param favorita          si esta marcada como favorita
 * @param rutaArchivo       ruta al MP3 o WAV local, {@code null} si la cancion es solo metadata
 * @param urlPortadaRemota  URL de la caratula elegida, {@code null} si no hay
 */
public record DatosCancion(String titulo, String artista, String album, String genero,
                           int anio, int duracionSegundos, int calificacion, boolean favorita,
                           String rutaArchivo, String urlPortadaRemota) {

    /**
     * Construye una cancion nueva con estos valores.
     *
     * @return una cancion recien creada, con identificador propio
     */
    public Cancion crearCancion() {
        Cancion cancion = new Cancion(titulo);
        aplicarA(cancion);
        return cancion;
    }

    /**
     * Vuelca estos valores sobre una cancion existente.
     *
     * <p>Pensado para usarse como la operacion de edicion que recibe
     * {@code BibliotecaService.editar(cancion, cambios)}.</p>
     *
     * @param cancion cancion a modificar
     */
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
    }

    /**
     * Toma los valores actuales de una cancion, para precargar el formulario de edicion.
     *
     * @param cancion cancion de la que copiar los datos
     * @return los datos de esa cancion
     */
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
                cancion.getUrlPortadaRemota());
    }
}
