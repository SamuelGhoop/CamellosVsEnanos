package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.ColeccionDeCanciones;
import com.eia.reproductor.modelo.Playlist;

import java.util.ArrayList;
import java.util.List;

/** Una lista hecha por el usuario, vista como coleccion reproducible. */
public class ColeccionPlaylist implements ColeccionDeCanciones {
    private final Playlist playlist;
    private final BibliotecaService biblioteca;

    /** Crea la coleccion. */
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
     * {@inheritDoc} En el orden en que el usuario las agrego, que es el que espera ver y el que
     * recibe el modo de orden de llegada.
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
