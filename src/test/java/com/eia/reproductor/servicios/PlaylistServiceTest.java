package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.Playlist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del servicio de listas de reproduccion.
 *
 * <p>Trabajan sobre una carpeta temporal: nunca tocan {@code data/playlists.json}.</p>
 */
@DisplayName("PlaylistService")
class PlaylistServiceTest {

    @TempDir
    Path carpeta;

    private PlaylistService servicio;

    private Path archivo() {
        return carpeta.resolve("playlists.json");
    }

    @BeforeEach
    void crearServicio() {
        servicio = new PlaylistService(archivo());
    }

    private static Cancion cancion(String titulo) {
        return new Cancion(titulo);
    }

    @Nested
    @DisplayName("Crear")
    class Crear {

        @Test
        @DisplayName("Crea una lista y la deja disponible")
        void creaUnaLista() {
            Playlist creada = servicio.crear("Para estudiar").orElseThrow();

            assertEquals("Para estudiar", creada.getNombre());
            assertEquals(1, servicio.tamanio());
        }

        @Test
        @DisplayName("Rechaza nombres repetidos aunque cambien las mayúsculas")
        void rechazaNombresRepetidos() {
            servicio.crear("Rock");

            // Dos listas con el mismo nombre serían indistinguibles en el selector.
            assertTrue(servicio.crear("rock").isEmpty());
            assertTrue(servicio.crear("  ROCK  ").isEmpty());
            assertEquals(1, servicio.tamanio());
            assertTrue(servicio.ultimoAviso().orElse("").contains("Ya existe"));
        }

        @Test
        @DisplayName("Rechaza nombres vacíos sin lanzar excepción")
        void rechazaNombresVacios() {
            assertTrue(servicio.crear(null).isEmpty());
            assertTrue(servicio.crear("   ").isEmpty());
            assertEquals(0, servicio.tamanio());
        }

        @Test
        @DisplayName("Rechaza nombres demasiado largos e informa del motivo")
        void rechazaNombresLargos() {
            assertTrue(servicio.crear("x".repeat(Playlist.LARGO_MAXIMO_NOMBRE + 1)).isEmpty());
            assertTrue(servicio.ultimoAviso().orElse("").contains("caracteres"));
        }
    }

    @Nested
    @DisplayName("Renombrar y borrar")
    class RenombrarYBorrar {

        @Test
        @DisplayName("Renombra conservando las canciones")
        void renombraConservandoCanciones() {
            Playlist lista = servicio.crear("Rock").orElseThrow();
            servicio.agregarCancion(lista, cancion("Creep"));

            assertTrue(servicio.renombrar(lista, "Rock de los 90"));

            assertEquals("Rock de los 90", lista.getNombre());
            assertEquals(1, lista.tamanio());
        }

        @Test
        @DisplayName("Renombrar a su propio nombre no se considera duplicado")
        void renombrarAlMismoNombre() {
            Playlist lista = servicio.crear("Rock").orElseThrow();

            assertTrue(servicio.renombrar(lista, "Rock"));
        }

        @Test
        @DisplayName("No se puede renombrar pisando otra lista")
        void noPisaOtraLista() {
            Playlist rock = servicio.crear("Rock").orElseThrow();
            servicio.crear("Pop");

            assertFalse(servicio.renombrar(rock, "Pop"));
            assertEquals("Rock", rock.getNombre());
        }

        @Test
        @DisplayName("Borrar una lista no borra sus canciones de la biblioteca")
        void borrarNoTocaLaBiblioteca() {
            BibliotecaService biblioteca = new BibliotecaService(
                    new PersistenciaService(carpeta.resolve("biblioteca.json")));
            Cancion creep = cancion("Creep");
            biblioteca.agregar(creep);
            Playlist lista = servicio.crear("Rock").orElseThrow();
            servicio.agregarCancion(lista, creep);

            assertTrue(servicio.eliminar(lista));

            assertEquals(0, servicio.tamanio());
            assertEquals(1, biblioteca.tamanio(), "la canción sigue en la biblioteca");
        }

        @Test
        @DisplayName("Borrar algo que no está no rompe nada")
        void borrarLoQueNoEsta() {
            assertFalse(servicio.eliminar(new Playlist("Fantasma")));
            assertFalse(servicio.eliminar(null));
        }
    }

    @Nested
    @DisplayName("Canciones")
    class CancionesDeLaLista {

        @Test
        @DisplayName("Agrega y quita")
        void agregaYQuita() {
            Playlist lista = servicio.crear("Rock").orElseThrow();
            Cancion creep = cancion("Creep");

            assertTrue(servicio.agregarCancion(lista, creep));
            assertTrue(lista.contiene(creep.getId()));

            assertTrue(servicio.quitarCancion(lista, creep));
            assertFalse(lista.contiene(creep.getId()));
        }

        @Test
        @DisplayName("Agregar dos veces avisa en vez de duplicar")
        void agregarDosVecesAvisa() {
            Playlist lista = servicio.crear("Rock").orElseThrow();
            Cancion creep = cancion("Creep");
            servicio.agregarCancion(lista, creep);

            assertFalse(servicio.agregarCancion(lista, creep));
            assertTrue(servicio.ultimoAviso().orElse("").contains("ya está"));
            assertEquals(1, lista.tamanio());
        }

        @Test
        @DisplayName("No agrega a una lista que no administra este servicio")
        void noAgregaAListasAjenas() {
            assertFalse(servicio.agregarCancion(new Playlist("Suelta"), cancion("Creep")));
        }

        @Test
        @DisplayName("Limpia las canciones borradas de la biblioteca")
        void limpiaHuerfanas() {
            BibliotecaService biblioteca = new BibliotecaService(
                    new PersistenciaService(carpeta.resolve("biblioteca.json")));
            Cancion sigue = cancion("Sigue");
            Cancion seVa = cancion("Se va");
            biblioteca.agregar(sigue);
            biblioteca.agregar(seVa);
            Playlist lista = servicio.crear("Rock").orElseThrow();
            servicio.agregarCancion(lista, sigue);
            servicio.agregarCancion(lista, seVa);

            biblioteca.eliminar(seVa);
            int descartadas = servicio.limpiarHuerfanas(biblioteca);

            assertEquals(1, descartadas);
            assertEquals(List.of(sigue.getId()), lista.idsCanciones());
        }
    }

    @Nested
    @DisplayName("Persistencia")
    class Persistencia {

        @Test
        @DisplayName("Guardar y volver a cargar conserva nombres, orden e identificadores")
        void viajeDeIdaYVuelta() {
            Playlist lista = servicio.crear("Para estudiar").orElseThrow();
            Cancion uno = cancion("Uno");
            Cancion dos = cancion("Dos");
            servicio.agregarCancion(lista, uno);
            servicio.agregarCancion(lista, dos);

            PlaylistService recargado = new PlaylistService(archivo());
            assertEquals(1, recargado.cargarDesdeDisco());

            Playlist copia = recargado.todas().get(0);
            assertEquals(lista.getId(), copia.getId());
            assertEquals("Para estudiar", copia.getNombre());
            assertEquals(List.of(uno.getId(), dos.getId()), copia.idsCanciones());
        }

        @Test
        @DisplayName("Sin archivo se empieza con cero listas")
        void sinArchivo() {
            assertEquals(0, new PlaylistService(carpeta.resolve("no-existe.json"))
                    .cargarDesdeDisco());
        }

        @Test
        @DisplayName("Un archivo dañado se respalda y se empieza de cero")
        void archivoCorrupto() throws IOException {
            Files.writeString(archivo(), "{ esto no es un arreglo");

            assertEquals(0, servicio.cargarDesdeDisco());

            // Se conserva por si se puede recuperar a mano: nunca se pisa en silencio.
            assertTrue(Files.exists(carpeta.resolve("playlists.json.bak")));
            assertTrue(servicio.ultimoAviso().orElse("").contains("dañado"));
        }

        @Test
        @DisplayName("Se saltan las listas sin nombre en vez de fallar entera la carga")
        void listaSinNombreSeSalta() throws IOException {
            Files.writeString(archivo(), """
                    [{"id":"a","canciones":[]},
                     {"id":"b","nombre":"Buena","canciones":["x"]}]
                    """);

            assertEquals(1, servicio.cargarDesdeDisco());
            assertEquals("Buena", servicio.todas().get(0).getNombre());
        }

        @Test
        @DisplayName("Una lista sin id se carga con uno nuevo")
        void listaSinIdRecibeUno() throws IOException {
            Files.writeString(archivo(), "[{\"nombre\":\"Sin id\"}]");

            assertEquals(1, servicio.cargarDesdeDisco());
            assertFalse(servicio.todas().get(0).getId().isBlank());
        }

        @Test
        @DisplayName("No deja temporales huérfanos")
        void sinTemporalesHuerfanos() throws IOException {
            servicio.crear("Una");
            servicio.crear("Otra");

            try (var entradas = Files.list(carpeta)) {
                assertEquals(1, entradas.count());
            }
        }
    }

    @Nested
    @DisplayName("Búsqueda")
    class Busqueda {

        @Test
        @DisplayName("Encuentra por nombre sin distinguir mayúsculas")
        void encuentraPorNombre() {
            Playlist lista = servicio.crear("Para Estudiar").orElseThrow();

            assertEquals(Optional.of(lista), servicio.porNombre("para estudiar"));
            assertEquals(Optional.of(lista), servicio.porNombre("  PARA ESTUDIAR "));
        }

        @Test
        @DisplayName("Lo que no existe devuelve vacío")
        void loQueNoExiste() {
            assertTrue(servicio.porNombre("Fantasma").isEmpty());
            assertTrue(servicio.porNombre(null).isEmpty());
        }
    }
}
