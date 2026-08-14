package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.ColeccionDeCanciones;

import java.util.List;

/**
 * Las canciones marcadas como favoritas, vistas como una lista de reproduccion.
 *
 * <p><b>No guarda nada.</b> Se calcula preguntandole a la biblioteca cuales tienen la estrella
 * puesta. Esa decision es la que hace que marcar una cancion la meta en la lista al instante y
 * desmarcarla la saque, sin que haya dos sitios donde apuntar lo mismo y sin riesgo de que se
 * contradigan.</p>
 *
 * <p>Por eso tampoco admite edicion directa: se edita poniendo y quitando estrellas, que es donde
 * el usuario ya espera hacerlo.</p>
 */
public class ColeccionFavoritas implements ColeccionDeCanciones {

    /** Nombre con la estrella delante, para distinguirla de las listas hechas a mano. */
    public static final String NOMBRE = "★ FAVORITAS";

    private final BibliotecaService biblioteca;

    /**
     * Crea la coleccion.
     *
     * @param biblioteca fuente unica de verdad de las canciones
     */
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

    /** {@inheritDoc} <p>Se edita con la estrella de cada cancion, no metiendolas a mano.</p> */
    @Override
    public boolean admiteEdicion() {
        return false;
    }
}
