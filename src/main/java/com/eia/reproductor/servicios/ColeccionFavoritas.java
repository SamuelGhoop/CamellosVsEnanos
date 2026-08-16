package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.ColeccionDeCanciones;

import java.util.List;

/** Las canciones marcadas como favoritas, vistas como una lista de reproduccion. */
public class ColeccionFavoritas implements ColeccionDeCanciones {
    /** Nombre con la estrella delante, para distinguirla de las listas hechas a mano. */
    public static final String NOMBRE = "★ FAVORITAS";

    private final BibliotecaService biblioteca;

    /** Crea la coleccion. */
    public ColeccionFavoritas(BibliotecaService biblioteca) {
        this.biblioteca = biblioteca;
    }

    @Override
    public String nombre() {
        return NOMBRE;
    }

    @Override
    public List<Cancion> canciones() {
        return biblioteca.filtrar(Cancion::isFavorita);
    }

    /** {@inheritDoc} Se edita con la estrella de cada cancion, no metiendolas a mano. */
    @Override
    public boolean admiteEdicion() {
        return false;
    }
}
