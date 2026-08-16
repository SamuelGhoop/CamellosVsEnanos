package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.ResultadoBusquedaApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del {@link MetadataApiService}. */
@DisplayName("Servicio de metadata (iTunes + MusicBrainz)")
class MetadataApiServiceTest {
    /** Respuesta real de iTunes, recortada a un resultado. */
    private static final String RESPUESTA_ITUNES = """
            {
             "resultCount":1,
             "results": [
              {"wrapperType":"track", "kind":"song", "artistName":"Queen",
               "collectionName":"A Night At The Opera (Deluxe Edition)",
               "trackName":"Bohemian Rhapsody",
               "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/8b/0a/ea/algo.jpg/100x100bb.jpg",
               "releaseDate":"1975-10-31T12:00:00Z",
               "trackTimeMillis":355155,
               "primaryGenreName":"Rock"}
             ]
            }
            """;

    /** Respuesta real de MusicBrainz, recortada a una grabacion. */
    private static final String RESPUESTA_MUSICBRAINZ = """
            {
              "recordings": [
                {
                  "id": "b1a9c0e9-d987-4042-ae91-78d6a3267d69",
                  "title": "Bohemian Rhapsody",
                  "length": 355000,
                  "artist-credit": [ { "name": "Queen", "artist": { "name": "Queen" } } ],
                  "releases": [
                    { "id": "1f3f2b76-1111-2222-3333-444455556666",
                      "title": "A Night at the Opera",
                      "date": "1975-11-21" }
                  ]
                }
              ]
            }
            """;

    @Nested
    @DisplayName("Mapeo de iTunes")
    class MapeoItunes {
        @Test
        @DisplayName("traduce todos los campos que pide el enunciado")
        void mapeaTodosLosCampos() {
            List<ResultadoBusquedaApi> resultados =
                    MetadataApiService.mapearItunes(RESPUESTA_ITUNES);

            assertEquals(1, resultados.size());
            ResultadoBusquedaApi cancion = resultados.get(0);
            assertEquals("Bohemian Rhapsody", cancion.titulo());
            assertEquals("Queen", cancion.artista());
            assertEquals("A Night At The Opera (Deluxe Edition)", cancion.album());
            assertEquals("Rock", cancion.genero());
            assertEquals(1975, cancion.anio(), "el anio sale de los 4 primeros caracteres de releaseDate");
            assertEquals(355, cancion.duracionSegundos(), "trackTimeMillis pasa de milisegundos a segundos");
            assertEquals(ResultadoBusquedaApi.FUENTE_ITUNES, cancion.fuente());
        }

        @Test
        @DisplayName("sube la caratula a alta resolucion")
        void caratulaEnAltaResolucion() {
            ResultadoBusquedaApi cancion = MetadataApiService.mapearItunes(RESPUESTA_ITUNES).get(0);

            assertTrue(cancion.urlPortadaMiniatura().endsWith("100x100bb.jpg"),
                    "la miniatura conserva el tamanio original");
            assertTrue(cancion.urlPortadaGrande().endsWith("600x600bb.jpg"),
                    "la grande cambia 100x100bb por 600x600bb");
        }

        @Test
        @DisplayName("una respuesta sin resultados devuelve lista vacia")
        void sinResultados() {
            assertTrue(MetadataApiService.mapearItunes("{\"resultCount\":0,\"results\":[]}").isEmpty());
        }

        @Test
        @DisplayName("descarta las entradas que no tienen titulo")
        void descartaEntradasSinTitulo() {
            String json = "{\"results\":[{\"artistName\":\"Alguien\"},"
                    + "{\"trackName\":\"Valida\",\"artistName\":\"Otro\"}]}";

            List<ResultadoBusquedaApi> resultados = MetadataApiService.mapearItunes(json);

            assertEquals(1, resultados.size());
            assertEquals("Valida", resultados.get(0).titulo());
        }

        @Test
        @DisplayName("los campos que falten no rompen el mapeo")
        void camposFaltantes() {
            String json = "{\"results\":[{\"trackName\":\"Minima\"}]}";

            ResultadoBusquedaApi cancion = MetadataApiService.mapearItunes(json).get(0);

            assertEquals("Minima", cancion.titulo());
            assertNull(cancion.artista());
            assertEquals(0, cancion.anio());
            assertEquals(0, cancion.duracionSegundos());
            assertNull(cancion.urlPortadaGrande());
        }

        @Test
        @DisplayName("un JSON que no es un objeto no revienta")
        void jsonInesperado() {
            assertTrue(MetadataApiService.mapearItunes("[]").isEmpty());
            assertTrue(MetadataApiService.mapearItunes("\"texto suelto\"").isEmpty());
        }
    }

    @Nested
    @DisplayName("Mapeo de MusicBrainz")
    class MapeoMusicBrainz {
        @Test
        @DisplayName("traduce titulo, artista, album, anio y duracion")
        void mapeaLoDisponible() {
            List<ResultadoBusquedaApi> resultados =
                    MetadataApiService.mapearMusicBrainz(RESPUESTA_MUSICBRAINZ);

            assertEquals(1, resultados.size());
            ResultadoBusquedaApi cancion = resultados.get(0);
            assertEquals("Bohemian Rhapsody", cancion.titulo());
            assertEquals("Queen", cancion.artista());
            assertEquals("A Night at the Opera", cancion.album());
            assertEquals(1975, cancion.anio());
            assertEquals(355, cancion.duracionSegundos());
            assertEquals(ResultadoBusquedaApi.FUENTE_MUSICBRAINZ, cancion.fuente());
        }

        @Test
        @DisplayName("arma la URL de la caratula con el mbid del lanzamiento")
        void urlDeCoverArtArchive() {
            ResultadoBusquedaApi cancion =
                    MetadataApiService.mapearMusicBrainz(RESPUESTA_MUSICBRAINZ).get(0);

            assertNotNull(cancion.urlPortadaGrande());
            assertTrue(cancion.urlPortadaGrande().startsWith("https://coverartarchive.org/release/"));
            assertTrue(cancion.urlPortadaGrande().endsWith("/front-500"));
            assertTrue(cancion.urlPortadaGrande().contains("1f3f2b76-1111-2222-3333-444455556666"));
        }

        @Test
        @DisplayName("MusicBrainz no informa genero y eso no es un error")
        void sinGenero() {
            assertNull(MetadataApiService.mapearMusicBrainz(RESPUESTA_MUSICBRAINZ).get(0).genero());
        }

        @Test
        @DisplayName("una grabacion sin lanzamientos se mapea igual, sin album ni caratula")
        void grabacionSinLanzamiento() {
            String json = "{\"recordings\":[{\"title\":\"Suelta\","
                    + "\"artist-credit\":[{\"name\":\"Nadie\"}]}]}";

            ResultadoBusquedaApi cancion = MetadataApiService.mapearMusicBrainz(json).get(0);

            assertEquals("Suelta", cancion.titulo());
            assertEquals("Nadie", cancion.artista());
            assertNull(cancion.album());
            assertNull(cancion.urlPortadaGrande());
            assertEquals(0, cancion.anio());
        }

        @Test
        @DisplayName("una respuesta vacia devuelve lista vacia")
        void sinGrabaciones() {
            assertTrue(MetadataApiService.mapearMusicBrainz("{\"recordings\":[]}").isEmpty());
            assertTrue(MetadataApiService.mapearMusicBrainz("{}").isEmpty());
        }
    }

    @Nested
    @DisplayName("Utilidades")
    class Utilidades {
        @Test
        @DisplayName("el anio se extrae de fechas en varios formatos")
        void anioDeFecha() {
            assertEquals(1975, MetadataApiService.anioDeFecha("1975-10-31T12:00:00Z"));
            assertEquals(1975, MetadataApiService.anioDeFecha("1975-11-21"));
            assertEquals(1975, MetadataApiService.anioDeFecha("1975"));
            assertEquals(0, MetadataApiService.anioDeFecha(null));
            assertEquals(0, MetadataApiService.anioDeFecha("s/f"));
            assertEquals(0, MetadataApiService.anioDeFecha("abcd-01-01"));
        }

        @Test
        @DisplayName("una URL que no tiene el patron esperado se deja como esta")
        void urlSinPatron() {
            String otra = "https://ejemplo.com/portada.png";
            assertEquals(otra, MetadataApiService.aAltaResolucion(otra));
            assertNull(MetadataApiService.aAltaResolucion(null));
        }
    }

    @Nested
    @DisplayName("Comportamiento sin red")
    class SinRed {
        @Test
        @DisplayName("una consulta vacia no dispara ninguna llamada")
        void consultaVacia() {
            MetadataApiService servicio = new MetadataApiService();

            assertTrue(servicio.buscar(null).isEmpty());
            assertTrue(servicio.buscar("").isEmpty());
            assertTrue(servicio.buscar("   ").isEmpty());
            assertTrue(servicio.ultimoAviso().isEmpty());
        }
    }
}
