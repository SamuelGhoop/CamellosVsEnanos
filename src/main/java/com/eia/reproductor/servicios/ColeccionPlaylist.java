package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.ColeccionDeCanciones;
import com.eia.reproductor.modelo.Playlist;

import java.util.ArrayList;
import java.util.List;

/**
 * Una lista hecha por el usuario, vista como coleccion reproducible.
 *
 * <p>Traduce los identificadores que guarda la {@link Playlist} a las canciones de verdad. Esa
 * traduccion se hace en cada consulta y no se cachea: si el usuario edita el titulo de una cancion,
 * la lista muestra el titulo nuevo sin tener que enterarse del cambio.</p>
 *
 * <p>Los identificadores que ya no existen en la biblioteca se saltan en silencio. Es lo que pasa
 * cuando se borra una cancion que estaba en varias listas, y no tiene sentido molestar al usuario
 * con eso: la limpieza definitiva la hace {@link PlaylistService}.</p>
 */
public class ColeccionPlaylist implements ColeccionDeCanciones {

    private final Playlist playlist;
    private final BibliotecaService biblioteca;

    /**
     * Crea la coleccion.
     *
     * @param playlist   lista con los identificadores
     * @param biblioteca de donde se resuelven
     */
    public ColeccionPlaylist(Playlist playlist, BibliotecaService biblioteca) {
        this.playlist = playlist;
        this.biblioteca = biblioteca;
    }

    /** @return la lista que hay detras, para poder editarla */
    public Playlist playlist() {
        return playlist;
    }

    @Override
    public String nombre() {
        return playlist.getNombre();
    }

    /**
     * {@inheritDoc}
     *
     * <p>En el orden en que el usuario las agrego, que es el que espera ver y el que recibe el modo
     * de orden de llegada.</p>
     */
    @Override
    public List<Cancion> canciones() {
        List<Cancion> resueltas = new ArrayList<>(playlist.tamanio());
        for (String id : playlist.idsCanciones()) {
            Cancion cancion = biblioteca.porId(id);
            if (cancion != null) {
                resueltas.add(cancion);
            }
        }
        return resueltas;
    }

    @Override
    public boolean admiteEdicion() {
        return true;
    }
}
