package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.ColeccionDeCanciones;
import com.eia.reproductor.modelo.Playlist;
import com.eia.reproductor.modos.ModoAlfabetico;
import com.eia.reproductor.modos.ModoAleatorio;
import com.eia.reproductor.modos.ModoOrdenLlegada;
import com.eia.reproductor.modos.ModoReproduccion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las tres colecciones reproducibles.
 *
 * <p>Lo que mas importa esta al final: que <b>cualquier</b> coleccion se pueda reproducir con
 * <b>cualquiera</b> de los tres modos. Es lo que hace que las listas no obliguen a tocar las
 * estructuras de datos.</p>
 */
class ColeccionesTest {

    @TempDir
    Path carpeta;

    private BibliotecaService biblioteca;
    private Cancion zombie;
    private Cancion africa;
    private Cancion creep;

    @BeforeEach
    void prepararBiblioteca() {
        biblioteca = new BibliotecaService(
                new PersistenciaService(carpeta.resolve("biblioteca.json")));
        zombie = new Cancion("Zombie");
        africa = new Cancion("Africa");
        creep = new Cancion("Creep");
        biblioteca.agregar(zombie);
        biblioteca.agregar(africa);
        biblioteca.agregar(creep);
    }

    private static List<String> titulosDe(ColeccionDeCanciones coleccion) {
        return coleccion.canciones().stream().map(Cancion::getTitulo).toList();
    }

    @Test
    @DisplayName("La biblioteca entera contiene todo y no se edita a mano")
    void coleccionBiblioteca() {
        ColeccionDeCanciones todas = new ColeccionBiblioteca(biblioteca);

        assertEquals(3, todas.tamanio());
        assertFalse(todas.admiteEdicion());
        assertFalse(todas.estaVacia());
    }

    @Test
    @DisplayName("Las favoritas se calculan solas al marcar la estrella")
    void favoritasSeCalculanSolas() {
        ColeccionDeCanciones favoritas = new ColeccionFavoritas(biblioteca);
        assertTrue(favoritas.estaVacia());

        biblioteca.alternarFavorita(creep);

        // No hay que avisarle a nadie: la colección se recalcula en cada consulta.
        assertEquals(List.of("Creep"), titulosDe(favoritas));

        biblioteca.alternarFavorita(creep);
        assertTrue(favoritas.estaVacia());
    }

    @Test
    @DisplayName("Las favoritas no se editan metiendo canciones a mano")
    void favoritasNoSeEditanAMano() {
        assertFalse(new ColeccionFavoritas(biblioteca).admiteEdicion());
    }

    @Test
    @DisplayName("Una lista personal respeta el orden en que se agregó")
    void listaPersonalRespetaElOrden() {
        Playlist lista = new Playlist("Mi lista");
        lista.agregar(zombie.getId());
        lista.agregar(africa.getId());

        ColeccionDeCanciones coleccion = new ColeccionPlaylist(lista, biblioteca);

        assertEquals(List.of("Zombie", "Africa"), titulosDe(coleccion));
        assertTrue(coleccion.admiteEdicion());
    }

    @Test
    @DisplayName("Una lista refleja los cambios de título sin enterarse")
    void listaReflejaLosCambios() {
        Playlist lista = new Playlist("Mi lista");
        lista.agregar(creep.getId());
        ColeccionDeCanciones coleccion = new ColeccionPlaylist(lista, biblioteca);

        biblioteca.editar(creep, cancion -> cancion.setTitulo("Creep (remaster)"));

        // Guarda identificadores, no copias: por eso no hay dos versiones que puedan discrepar.
        assertEquals(List.of("Creep (remaster)"), titulosDe(coleccion));
    }

    @Test
    @DisplayName("Una lista se salta en silencio las canciones ya borradas")
    void listaSaltaLasBorradas() {
        Playlist lista = new Playlist("Mi lista");
        lista.agregar(zombie.getId());
        lista.agregar(africa.getId());
        ColeccionDeCanciones coleccion = new ColeccionPlaylist(lista, biblioteca);

        biblioteca.eliminar(zombie);

        assertEquals(List.of("Africa"), titulosDe(coleccion));
    }

    @Test
    @DisplayName("Cualquier colección suena con cualquiera de los tres modos")
    void cualquierColeccionConCualquierModo() {
        Playlist lista = new Playlist("Mi lista");
        lista.agregar(zombie.getId());
        lista.agregar(africa.getId());

        List<ColeccionDeCanciones> colecciones = List.of(
                new ColeccionBiblioteca(biblioteca),
                new ColeccionFavoritas(biblioteca),
                new ColeccionPlaylist(lista, biblioteca));
        biblioteca.alternarFavorita(creep);

        for (ColeccionDeCanciones coleccion : colecciones) {
            for (ModoReproduccion modo : List.of(
                    new ModoAleatorio(), new ModoOrdenLlegada(), new ModoAlfabetico())) {

                // Este es el punto del diseño: los modos reciben Iterable<Cancion> y no saben ni
                // les importa de qué colección salió.
                modo.cargar(coleccion.canciones());

                assertEquals(coleccion.tamanio(), modo.listaReproduccion().size(),
                        coleccion.nombre() + " con " + modo.nombre());
                if (!coleccion.estaVacia()) {
                    assertTrue(modo.hayMas(), coleccion.nombre() + " con " + modo.nombre());
                }
            }
        }
    }

    @Test
    @DisplayName("Una lista vacía no rompe ningún modo")
    void listaVaciaNoRompeNada() {
        ColeccionDeCanciones vacia = new ColeccionPlaylist(new Playlist("Vacía"), biblioteca);

        for (ModoReproduccion modo : List.of(
                new ModoAleatorio(), new ModoOrdenLlegada(), new ModoAlfabetico())) {
            modo.cargar(vacia.canciones());

            assertFalse(modo.hayMas(), modo.nombre());
            assertTrue(modo.listaReproduccion().isEmpty(), modo.nombre());
        }
    }
}
