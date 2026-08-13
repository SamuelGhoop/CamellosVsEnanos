package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;

/**
 * Contrato de quien quiere enterarse de los cambios en la biblioteca.
 *
 * <p>Segunda interfaz del proyecto, junto a {@code ModoReproduccion}. Permite que
 * {@link BibliotecaService} avise de altas, bajas y ediciones <b>sin conocer a nadie</b>: no sabe
 * si quien escucha es el controlador de la interfaz, un modo de reproduccion o un registro de
 * estadisticas. Esa es la razon de ser del patron observador.</p>
 *
 * <p>Todos los metodos tienen implementacion vacia por defecto, asi cada observador sobrescribe
 * unicamente los eventos que le interesan.</p>
 *
 * <p><b>Sobre {@link #antesDeEditar(Cancion)} y {@link #despuesDeEditar(Cancion)}.</b> Editar no es
 * un solo evento sino una ventana con un antes y un despues, y eso no es un capricho: el modo
 * alfabetico guarda las canciones en un arbol <i>ordenado por titulo</i>. Si el usuario le cambia
 * el titulo a una cancion que ya esta dentro del arbol, el nodo queda ubicado segun un titulo que
 * ya no existe y el arbol no vuelve a encontrarla nunca. Por eso hay que sacarla antes de
 * modificarla y volver a insertarla despues.</p>
 *
 * @see BibliotecaService
 */
public interface ObservadorBiblioteca {

    /**
     * Se agrego una cancion nueva a la biblioteca.
     *
     * @param cancion la cancion agregada
     */
    default void cancionAgregada(Cancion cancion) {
    }

    /**
     * Se elimino una cancion de la biblioteca.
     *
     * @param cancion la cancion eliminada
     */
    default void cancionEliminada(Cancion cancion) {
    }

    /**
     * Una cancion esta a punto de cambiar de datos.
     *
     * <p>Quien ordene sus elementos por el contenido de la cancion debe retirarla aqui.</p>
     *
     * @param cancion la cancion que va a cambiar, todavia con sus datos viejos
     */
    default void antesDeEditar(Cancion cancion) {
    }

    /**
     * Una cancion termino de cambiar de datos.
     *
     * <p>Quien la haya retirado en {@link #antesDeEditar(Cancion)} debe reubicarla aqui.</p>
     *
     * @param cancion la cancion ya modificada
     */
    default void despuesDeEditar(Cancion cancion) {
    }

    /**
     * La biblioteca se reemplazo por completo, por ejemplo al cargarla desde disco.
     *
     * @param canciones el contenido nuevo de la biblioteca
     */
    default void bibliotecaRecargada(Iterable<Cancion> canciones) {
    }
}
