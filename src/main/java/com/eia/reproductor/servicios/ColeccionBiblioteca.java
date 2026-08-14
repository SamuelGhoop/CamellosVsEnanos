package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.ColeccionDeCanciones;

import java.util.List;

/**
 * La biblioteca entera, vista como una coleccion reproducible.
 *
 * <p>Es la coleccion por defecto y la que existia antes de que hubiera listas: convertirla en una
 * mas es lo que permite que el resto del codigo trate todas por igual, sin un caso especial para
 * "todavia no hay lista elegida".</p>
 */
public class ColeccionBiblioteca implements ColeccionDeCanciones {

    private final BibliotecaService biblioteca;

    /**
     * Crea la coleccion.
     *
     * @param biblioteca fuente unica de verdad de las canciones
     */
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

    /** {@inheritDoc} <p>Contiene todo por definicion: no hay nada que agregarle.</p> */
    @Override
    public boolean admiteEdicion() {
        return false;
    }
}
