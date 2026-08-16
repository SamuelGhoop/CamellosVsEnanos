package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;

/** Contrato de quien quiere enterarse de los cambios en la biblioteca. */
public interface ObservadorBiblioteca {
    /** Se agrego una cancion nueva a la biblioteca. */
    default void cancionAgregada(Cancion cancion) {
    }

    /** Se elimino una cancion de la biblioteca. */
    default void cancionEliminada(Cancion cancion) {
    }

    /** Una cancion esta a punto de cambiar de datos. */
    default void antesDeEditar(Cancion cancion) {
    }

    /** Una cancion termino de cambiar de datos. */
    default void despuesDeEditar(Cancion cancion) {
    }

    /** La biblioteca se reemplazo por completo, por ejemplo al cargarla desde disco. */
    default void bibliotecaRecargada(Iterable<Cancion> canciones) {
    }
}
