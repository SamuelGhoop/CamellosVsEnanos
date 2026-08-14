package com.eia.reproductor.modelo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de la lista de reproduccion hecha por el usuario. */
@DisplayName("Playlist")
class PlaylistTest {

    private Playlist lista;

    @BeforeEach
    void crearLista() {
        lista = new Playlist("Para estudiar");
    }

    @Nested
    @DisplayName("Nombre")
    class Nombre {

        @Test
        @DisplayName("Se recortan los espacios de los extremos")
        void recortaEspacios() {
            assertEquals("Para estudiar", new Playlist("  Para estudiar  ").getNombre());
        }

        @Test
        @DisplayName("Rechaza nombres vacíos")
        void rechazaNombresVacios() {
            assertThrows(IllegalArgumentException.class, () -> new Playlist(null));
            assertThrows(IllegalArgumentException.class, () -> new Playlist(""));
            assertThrows(IllegalArgumentException.class, () -> new Playlist("   "));
        }

        @Test
        @DisplayName("Rechaza nombres que no caben en el selector")
        void rechazaNombresLargos() {
            String largo = "x".repeat(Playlist.LARGO_MAXIMO_NOMBRE + 1);

            assertThrows(IllegalArgumentException.class, () -> new Playlist(largo));
        }

        @Test
        @DisplayName("Acepta justo el largo máximo")
        void aceptaElLargoMaximo() {
            String justo = "x".repeat(Playlist.LARGO_MAXIMO_NOMBRE);

            assertEquals(justo, new Playlist(justo).getNombre());
        }

        @Test
        @DisplayName("Renombrar no cambia la identidad de la lista")
        void renombrarNoCambiaLaIdentidad() {
            String id = lista.getId();

            lista.setNombre("Para dormir");

            // Si renombrar cambiara el id, la lista se convertiría en otra al guardarla.
            assertEquals(id, lista.getId());
            assertEquals("Para dormir", lista.getNombre());
        }
    }

    @Nested
    @DisplayName("Canciones")
    class Canciones {

        @Test
        @DisplayName("Conserva el orden en que se agregaron")
        void conservaElOrden() {
            lista.agregar("uno");
            lista.agregar("dos");
            lista.agregar("tres");

            // Es el orden que ve el usuario y el que recibe el modo de orden de llegada.
            assertEquals(List.of("uno", "dos", "tres"), lista.idsCanciones());
        }

        @Test
        @DisplayName("No admite la misma canción dos veces")
        void noAdmiteRepetidas() {
            assertTrue(lista.agregar("uno"));
            assertFalse(lista.agregar("uno"));

            assertEquals(1, lista.tamanio());
        }

        @Test
        @DisplayName("Ignora identificadores vacíos")
        void ignoraIdsVacios() {
            assertFalse(lista.agregar(null));
            assertFalse(lista.agregar(""));
            assertFalse(lista.agregar("   "));

            assertEquals(0, lista.tamanio());
        }

        @Test
        @DisplayName("Quitar saca solo la indicada")
        void quitarSacaSoloLaIndicada() {
            lista.agregar("uno");
            lista.agregar("dos");

            assertTrue(lista.quitar("uno"));
            assertFalse(lista.quitar("uno"));
            assertEquals(List.of("dos"), lista.idsCanciones());
        }

        @Test
        @DisplayName("contiene responde sin tocar la biblioteca")
        void contieneResponde() {
            lista.agregar("uno");

            assertTrue(lista.contiene("uno"));
            assertFalse(lista.contiene("dos"));
        }

        @Test
        @DisplayName("La lista devuelta no deja modificar la de adentro")
        void laListaDevueltaEsInmutable() {
            lista.agregar("uno");

            assertThrows(UnsupportedOperationException.class,
                    () -> lista.idsCanciones().add("colado"));
        }
    }

    @Nested
    @DisplayName("Limpieza de huérfanas")
    class Huerfanas {

        @Test
        @DisplayName("Descarta las canciones que ya no están en la biblioteca")
        void descartaLasQueYaNoExisten() {
            lista.agregar("vive");
            lista.agregar("borrada");
            lista.agregar("tambien-vive");

            int descartadas = lista.descartarHuerfanas(Set.of("vive", "tambien-vive"));

            assertEquals(1, descartadas);
            assertEquals(List.of("vive", "tambien-vive"), lista.idsCanciones());
        }

        @Test
        @DisplayName("Sin huérfanas no descarta nada")
        void sinHuerfanasNoDescartaNada() {
            lista.agregar("vive");

            assertEquals(0, lista.descartarHuerfanas(Set.of("vive")));
            assertEquals(1, lista.tamanio());
        }

        @Test
        @DisplayName("Si se borró todo, la lista queda vacía pero sigue existiendo")
        void puedeQuedarVacia() {
            lista.agregar("borrada");

            assertEquals(1, lista.descartarHuerfanas(Set.of()));
            assertEquals(0, lista.tamanio());
            assertEquals("Para estudiar", lista.getNombre());
        }
    }

    @Nested
    @DisplayName("Identidad")
    class Identidad {

        @Test
        @DisplayName("Dos listas con el mismo nombre son distintas")
        void mismoNombreNoEsLaMismaLista() {
            assertNotEquals(new Playlist("Rock"), new Playlist("Rock"));
        }

        @Test
        @DisplayName("La misma lista recargada del disco es la misma")
        void mismoIdEsLaMismaLista() {
            Playlist recargada = new Playlist(lista.getId(), "Otro nombre");

            assertEquals(lista, recargada);
            assertEquals(lista.hashCode(), recargada.hashCode());
        }
    }
}
