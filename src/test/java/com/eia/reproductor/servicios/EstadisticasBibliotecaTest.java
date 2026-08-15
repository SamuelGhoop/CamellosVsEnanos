package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del resumen de reproducciones.
 *
 * <p>Son las cuentas que salen en la ventana de estadisticas. Si estuvieran mal, nadie lo notaria
 * mirando la pantalla —los numeros parecerian plausibles igual— asi que se comprueban aqui.</p>
 */
class EstadisticasBibliotecaTest {

    /**
     * Cancion con todo lo que mira el resumen.
     *
     * @param duracion en segundos
     * @param veces    cuantas veces se ha reproducido
     */
    private static Cancion cancion(String titulo, String artista, String genero,
                                   int duracion, int veces) {
        Cancion cancion = new Cancion(titulo);
        cancion.setArtista(artista);
        cancion.setGenero(genero);
        cancion.setDuracionSegundos(duracion);
        cancion.setVecesReproducida(veces);
        return cancion;
    }

    @Test
    @DisplayName("Una biblioteca sin reproducir da todo en cero")
    void bibliotecaSinReproducir() {
        EstadisticasBiblioteca resumen = EstadisticasBiblioteca.de(
                List.of(cancion("Zombie", "The Cranberries", "Rock", 305, 0)));

        assertTrue(resumen.vacias());
        assertEquals(0, resumen.totalReproducciones());
        assertEquals(0, resumen.distintasSonadas());
        assertEquals(0, resumen.minutosEscuchados());
        assertTrue(resumen.masEscuchadas().isEmpty());
        assertNull(resumen.artistaTop());
        assertNull(resumen.generoTop());
    }

    @Test
    @DisplayName("Una biblioteca vacía tampoco rompe nada")
    void bibliotecaVacia() {
        assertTrue(EstadisticasBiblioteca.de(List.of()).vacias());
    }

    @Test
    @DisplayName("El total suma las veces, no las canciones")
    void totalSumaLasVeces() {
        EstadisticasBiblioteca resumen = EstadisticasBiblioteca.de(List.of(
                cancion("Zombie", "The Cranberries", "Rock", 300, 3),
                cancion("Africa", "Toto", "Pop", 240, 2),
                cancion("Creep", "Radiohead", "Rock", 180, 0)));

        assertEquals(5, resumen.totalReproducciones(), "3 + 2, no 2 canciones");
        assertEquals(2, resumen.distintasSonadas(), "Creep no ha sonado nunca");
        assertFalse(resumen.vacias());
    }

    @Test
    @DisplayName("Los minutos cuentan cada repetición")
    void minutosCuentanCadaRepeticion() {
        EstadisticasBiblioteca resumen = EstadisticasBiblioteca.de(List.of(
                cancion("Zombie", "The Cranberries", "Rock", 300, 3),
                cancion("Africa", "Toto", "Pop", 240, 2)));

        // 300*3 + 240*2 = 1380 s = 23 min. Escuchar algo tres veces son tres veces su duración.
        assertEquals(23, resumen.minutosEscuchados());
    }

    @Test
    @DisplayName("El artista y el género top suman todas sus canciones")
    void topPorSumaYNoPorUnaSolaCancion() {
        EstadisticasBiblioteca resumen = EstadisticasBiblioteca.de(List.of(
                cancion("Karma Police", "Radiohead", "Rock", 200, 3),
                cancion("Creep", "Radiohead", "Rock", 200, 3),
                // Gana en solitario, pero su artista suma menos que los dos de Radiohead juntos.
                cancion("Africa", "Toto", "Pop", 200, 5)));

        assertEquals("Radiohead", resumen.artistaTop(), "6 contra 5");
        assertEquals("Rock", resumen.generoTop());
    }

    @Test
    @DisplayName("El podio ordena de más a menos y se queda en cinco")
    void podioOrdenadoYRecortado() {
        EstadisticasBiblioteca resumen = EstadisticasBiblioteca.de(List.of(
                cancion("Sexta", "A", "Rock", 100, 1),
                cancion("Primera", "A", "Rock", 100, 60),
                cancion("Tercera", "A", "Rock", 100, 40),
                cancion("Segunda", "A", "Rock", 100, 50),
                cancion("Quinta", "A", "Rock", 100, 20),
                cancion("Cuarta", "A", "Rock", 100, 30)));

        assertEquals(List.of("Primera", "Segunda", "Tercera", "Cuarta", "Quinta"),
                resumen.masEscuchadas().stream().map(Cancion::getTitulo).toList());
    }

    @Test
    @DisplayName("Con el mismo número de reproducciones desempata el título")
    void desempatePorTitulo() {
        // Sin desempate, el podio cambiaría de orden entre una apertura y la siguiente.
        EstadisticasBiblioteca resumen = EstadisticasBiblioteca.de(List.of(
                cancion("Zombie", "A", "Rock", 100, 7),
                cancion("Africa", "B", "Pop", 100, 7)));

        assertEquals(List.of("Africa", "Zombie"),
                resumen.masEscuchadas().stream().map(Cancion::getTitulo).toList());
    }

    @Test
    @DisplayName("Lo que no tiene artista se agrupa bajo Desconocido, no bajo una etiqueta vacía")
    void loQueNoTieneArtistaVaJunto() {
        // Cancion normaliza los campos en blanco a "Desconocido", así que aquí nunca llega una
        // cadena vacía: "sin artista" es un grupo más y se cuenta como tal.
        EstadisticasBiblioteca resumen = EstadisticasBiblioteca.de(List.of(
                cancion("Sin datos", "  ", "", 100, 9)));

        assertEquals(9, resumen.totalReproducciones());
        assertEquals(Cancion.TEXTO_DESCONOCIDO, resumen.artistaTop());
        assertEquals(Cancion.TEXTO_DESCONOCIDO, resumen.generoTop());
    }

    @Test
    @DisplayName("El podio que se entrega no se puede modificar")
    void podioInmutable() {
        List<Cancion> podio = EstadisticasBiblioteca.de(List.of(
                cancion("Zombie", "A", "Rock", 100, 1))).masEscuchadas();

        assertThrows(UnsupportedOperationException.class, () -> podio.clear());
    }
}
