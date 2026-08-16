package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.ColeccionDeCanciones;

import java.util.List;

/** La biblioteca entera, vista como una coleccion reproducible. */
public class ColeccionBiblioteca implements ColeccionDeCanciones {
    private final BibliotecaService biblioteca;

    /** Crea la coleccion. */
    public ColeccionBiblioteca(BibliotecaService biblioteca) {
        this.biblioteca = biblioteca;
    }

    @Override
    public String nombre() {
        return "TODA LA BIBLIOTECA";
    }

    @Override
    public List<Cancion> canciones() {
        return biblioteca.todas();
    }

    /** {@inheritDoc} Contiene todo por definicion: no hay nada que agregarle. */
    @Override
    public boolean admiteEdicion() {
        return false;
    }
}
