package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del filtro por campo.
 *
 * <p>Lo que se comprueba aqui es que filtrar por GÉNERO mire de verdad el genero y no acabe
 * colando canciones porque la palabra aparezca en el titulo, y que las tildes no impidan encontrar
 * nada.</p>
 */
class FiltroDeCampoTest {

    private static Cancion cancion(String titulo, String artista, String album, String genero) {
        Cancion cancion = new Cancion(titulo);
        cancion.setArtista(artista);
        cancion.setAlbum(album);
        cancion.setGenero(genero);
        return cancion;
    }

    private static final Cancion ZOMBIE =
            cancion("Zombie", "The Cranberries", "No Need to Argue", "Rock");
    private static final Cancion CLASICA =
            cancion("Rock a mi manera", "Orquesta", "Clásicos", "Música clásica");

    @Test
    @DisplayName("Cada campo mira solo su campo")
    void cadaCampoMiraLoSuyo() {
        // "Rock" está en el título de la segunda y en el género de la primera: con el filtro
        // puesto en GÉNERO, la del título no debe colarse.
        assertTrue(FiltroDeCampo.GENERO.coincide(ZOMBIE, "Rock"));
        assertFalse(FiltroDeCampo.GENERO.coincide(CLASICA, "Rock"));
        assertTrue(FiltroDeCampo.TITULO.coincide(CLASICA, "Rock"));
        assertFalse(FiltroDeCampo.TITULO.coincide(ZOMBIE, "Cranberries"));
        assertTrue(FiltroDeCampo.ARTISTA.coincide(ZOMBIE, "Cranberries"));
        assertTrue(FiltroDeCampo.ALBUM.coincide(ZOMBIE, "Argue"));
    }

    @Test
    @DisplayName("TODO busca en título, artista y álbum a la vez")
    void todoBuscaEnVariosCampos() {
        assertTrue(FiltroDeCampo.TODO.coincide(ZOMBIE, "Zombie"));
        assertTrue(FiltroDeCampo.TODO.coincide(ZOMBIE, "Cranberries"));
        assertTrue(FiltroDeCampo.TODO.coincide(ZOMBIE, "Argue"));
        // El género queda fuera a propósito: es el mismo criterio que tenía la búsqueda de antes,
        // y para eso está el filtro por campo.
        assertFalse(FiltroDeCampo.TODO.coincide(ZOMBIE, "Rock"));
    }

    @Test
    @DisplayName("Ni las tildes ni las mayúsculas estorban")
    void ignoraTildesYMayusculas() {
        assertTrue(FiltroDeCampo.GENERO.coincide(CLASICA, "musica clasica"));
        assertTrue(FiltroDeCampo.GENERO.coincide(CLASICA, "MÚSICA"));
        assertTrue(FiltroDeCampo.ALBUM.coincide(CLASICA, "clasicos"));
    }

    @Test
    @DisplayName("Sin texto pasan todas")
    void sinTextoPasanTodas() {
        for (FiltroDeCampo campo : FiltroDeCampo.values()) {
            assertTrue(campo.coincide(ZOMBIE, null), campo.etiqueta());
            assertTrue(campo.coincide(ZOMBIE, "   "), campo.etiqueta());
        }
    }

    @Test
    @DisplayName("Los valores del desplegable salen de la biblioteca, sin repetir y ordenados")
    void valoresDerivadosDeLaBiblioteca() {
        List<Cancion> biblioteca = List.of(
                cancion("A", "Toto", "X", "Pop"),
                cancion("B", "Radiohead", "Y", "rock"),
                cancion("C", "Toto", "Z", "Rock"),
                cancion("D", "Soda", "W", "  "));

        // "rock" y "Rock" son el mismo género: aparece una sola vez, y ordenado alfabéticamente.
        // El género en blanco entra como "Desconocido" —así lo guarda Cancion— y es un valor útil:
        // sirve para encontrar justo lo que falta por etiquetar.
        assertEquals(List.of(Cancion.TEXTO_DESCONOCIDO, "Pop", "rock"),
                FiltroDeCampo.GENERO.valoresEn(biblioteca));
        assertEquals(List.of("Radiohead", "Soda", "Toto"),
                FiltroDeCampo.ARTISTA.valoresEn(biblioteca));
    }

    @Test
    @DisplayName("TODO no ofrece valores porque no mira un campo único")
    void todoNoTieneValores() {
        assertTrue(FiltroDeCampo.TODO.valoresEn(List.of(ZOMBIE)).isEmpty());
    }

    @Test
    @DisplayName("Una etiqueta desconocida cae en TODO en vez de reventar")
    void etiquetaDesconocida() {
        assertEquals(FiltroDeCampo.GENERO, FiltroDeCampo.porEtiqueta("GÉNERO"));
        assertEquals(FiltroDeCampo.TODO, FiltroDeCampo.porEtiqueta(null));
        assertEquals(FiltroDeCampo.TODO, FiltroDeCampo.porEtiqueta("GENERO"));
        assertEquals(FiltroDeCampo.TODO, FiltroDeCampo.porEtiqueta("lo que sea"));
    }

    @Test
    @DisplayName("El desplegable ofrece las cinco opciones, con TODO primero")
    void etiquetasParaElDesplegable() {
        assertEquals(List.of("TODO", "TÍTULO", "ARTISTA", "ÁLBUM", "GÉNERO"),
                FiltroDeCampo.etiquetas());
    }
}
