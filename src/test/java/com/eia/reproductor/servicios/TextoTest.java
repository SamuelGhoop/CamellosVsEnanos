package com.eia.reproductor.servicios;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Pruebas del normalizador de texto.
 *
 * <p>Lo usan la busqueda, los filtros y el emparejado de resultados de Spotify. Es poco codigo,
 * pero si se equivoca, los tres fallan a la vez y por motivos que parecen distintos.</p>
 */
class TextoTest {

    @Test
    @DisplayName("Quita tildes y baja a minúsculas")
    void quitaTildesYMayusculas() {
        assertEquals("angel", Texto.plano("Ángel"));
        assertEquals("musica clasica", Texto.plano("MÚSICA CLÁSICA"));
        assertEquals("nino", Texto.plano("Niño"), "la eñe pierde la virgulilla, igual que la n");
    }

    @Test
    @DisplayName("Un texto nulo da cadena vacía, no una excepción")
    void nuloDaVacio() {
        assertEquals("", Texto.plano(null));
        assertEquals("", Texto.soloLetrasYNumeros(null));
    }

    @Test
    @DisplayName("La versión de búsqueda conserva los espacios")
    void laDeBusquedaConservaEspacios() {
        // Tiene que conservarlos: si los borrara, buscar "no need" no encontraría "No Need to
        // Argue", porque el texto guardado tendría las palabras pegadas.
        assertEquals("no need to argue", Texto.plano("No Need to Argue"));
    }

    @Test
    @DisplayName("La versión estricta borra espacios y signos")
    void laEstrictaBorraSignos() {
        assertEquals("zombieremastered2011",
                Texto.soloLetrasYNumeros("Zombie - Remastered 2011"));
        assertEquals("creepfeatalguien", Texto.soloLetrasYNumeros("Creep (feat. Alguien)"));
    }

    @Test
    @DisplayName("Las dos versiones no son intercambiables")
    void sonDosCriteriosDistintos() {
        // Si alguien las unificara, este test lo caza: la de búsqueda dejaría de encontrar por
        // palabras sueltas, o la estricta empezaría a fallar con las coletillas de Spotify.
        assertNotEquals(Texto.plano("Hey Jude"), Texto.soloLetrasYNumeros("Hey Jude"));
    }
}
