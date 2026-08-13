package com.eia.reproductor.modos;

import com.eia.reproductor.modelo.Cancion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del {@link ModoAlfabetico}: recorrido inorden sobre el arbol vivo. */
@DisplayName("Modo 3 - Alfabetico (arbol binario de busqueda)")
class ModoAlfabeticoTest {

    private ModoAlfabetico modo;
    private List<Cancion> biblioteca;

    @BeforeEach
    void preparar() {
        modo = new ModoAlfabetico();
        // Se cargan desordenadas a proposito: el orden lo debe imponer el arbol, no la entrada.
        biblioteca = new ArrayList<>();
        for (String titulo : List.of("Creep", "Bohemian Rhapsody", "Enter Sandman", "Africa", "Dream On")) {
            biblioteca.add(new Cancion(titulo));
        }
    }

    private List<String> titulosDe(List<Cancion> canciones) {
        List<String> titulos = new ArrayList<>();
        for (Cancion cancion : canciones) {
            titulos.add(cancion.getTitulo());
        }
        return titulos;
    }

    @Test
    @DisplayName("identifica su nombre y su estructura")
    void identidad() {
        assertEquals("Alfabético", modo.nombre());
        assertEquals("Árbol Binario de Búsqueda", modo.estructuraUsada());
        assertTrue(modo.permiteAnterior());
    }

    @Test
    @DisplayName("la lista de reproduccion sale en orden alfabetico aunque se cargue desordenada")
    void ordenAlfabetico() {
        modo.cargar(biblioteca);

        assertEquals(
                List.of("Africa", "Bohemian Rhapsody", "Creep", "Dream On", "Enter Sandman"),
                titulosDe(modo.listaReproduccion()));
    }

    @Test
    @DisplayName("la primera llamada a siguiente() arranca por la primera alfabeticamente")
    void arrancaPorElMinimo() {
        modo.cargar(biblioteca);

        assertEquals("Africa", modo.siguiente().getTitulo());
    }

    @Test
    @DisplayName("avanzar recorre el arbol en orden inorden completo")
    void avanceInorden() {
        modo.cargar(biblioteca);
        List<String> recorridas = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            recorridas.add(modo.siguiente().getTitulo());
        }

        assertEquals(titulosDe(modo.listaReproduccion()), recorridas);
    }

    @Test
    @DisplayName("al pasar de la ultima se vuelve a la primera")
    void vueltaCircularAlAvanzar() {
        modo.cargar(biblioteca);
        for (int i = 0; i < 5; i++) {
            modo.siguiente();
        }
        assertEquals("Enter Sandman", modo.actual().getTitulo());

        // Decision documentada del modo: comportamiento circular en lugar de detenerse.
        assertEquals("Africa", modo.siguiente().getTitulo());
    }

    @Test
    @DisplayName("al retroceder desde la primera se salta a la ultima")
    void vueltaCircularAlRetroceder() {
        modo.cargar(biblioteca);
        modo.siguiente();
        assertEquals("Africa", modo.actual().getTitulo());

        assertEquals("Enter Sandman", modo.anterior().getTitulo());
    }

    @Test
    @DisplayName("retroceder deshace exactamente lo avanzado")
    void retrocesoSimetrico() {
        modo.cargar(biblioteca);
        modo.siguiente();
        modo.siguiente();
        modo.siguiente();
        assertEquals("Creep", modo.actual().getTitulo());

        assertEquals("Bohemian Rhapsody", modo.anterior().getTitulo());
        assertEquals("Africa", modo.anterior().getTitulo());
    }

    @Test
    @DisplayName("agregar una cancion la coloca en su lugar alfabetico sin reconstruir nada")
    void agregarSeUbicaSolo() {
        modo.cargar(biblioteca);
        modo.siguiente();
        modo.siguiente();
        assertEquals("Bohemian Rhapsody", modo.actual().getTitulo());

        modo.agregar(new Cancion("Come Together"));

        // La nueva cancion se intercala y la navegacion la encuentra sin recargar el modo.
        assertEquals(
                List.of("Africa", "Bohemian Rhapsody", "Come Together", "Creep", "Dream On", "Enter Sandman"),
                titulosDe(modo.listaReproduccion()));
        assertEquals("Come Together", modo.siguiente().getTitulo());
    }

    @Test
    @DisplayName("eliminar una cancion cualquiera mantiene el orden")
    void eliminarOtraCancion() {
        modo.cargar(biblioteca);
        modo.siguiente();
        Cancion aEliminar = modo.listaReproduccion().get(2);

        modo.eliminar(aEliminar);

        assertEquals(
                List.of("Africa", "Bohemian Rhapsody", "Dream On", "Enter Sandman"),
                titulosDe(modo.listaReproduccion()));
        assertEquals("Africa", modo.actual().getTitulo(), "lo que sonaba no debe cambiar");
    }

    @Test
    @DisplayName("eliminar la cancion que suena salta a su sucesor")
    void eliminarLaQueSuenaSaltaAlSucesor() {
        modo.cargar(biblioteca);
        modo.siguiente();
        modo.siguiente();
        Cancion sonando = modo.actual();
        assertEquals("Bohemian Rhapsody", sonando.getTitulo());

        modo.eliminar(sonando);

        assertEquals("Creep", modo.actual().getTitulo(),
                "debe reposicionarse en el sucesor inorden");
        assertEquals("Dream On", modo.siguiente().getTitulo(),
                "la navegacion tiene que seguir funcionando tras el borrado");
    }

    @Test
    @DisplayName("eliminar la ultima cancion mientras suena salta a su predecesor")
    void eliminarLaUltimaMientrasSuena() {
        modo.cargar(biblioteca);
        for (int i = 0; i < 5; i++) {
            modo.siguiente();
        }
        Cancion ultima = modo.actual();
        assertEquals("Enter Sandman", ultima.getTitulo());

        modo.eliminar(ultima);

        assertEquals("Dream On", modo.actual().getTitulo());
    }

    @Test
    @DisplayName("eliminar la unica cancion deja el modo vacio")
    void eliminarUnicaCancion() {
        Cancion unica = new Cancion("Unica");
        modo.cargar(List.of(unica));
        modo.siguiente();

        modo.eliminar(unica);

        assertFalse(modo.hayMas());
        assertNull(modo.actual());
    }

    @Test
    @DisplayName("con una sola cancion la vuelta circular la devuelve siempre a ella")
    void unaSolaCancion() {
        Cancion unica = new Cancion("Unica");
        modo.cargar(List.of(unica));

        assertEquals(unica, modo.siguiente());
        assertEquals(unica, modo.siguiente());
        assertEquals(unica, modo.anterior());
    }

    @Test
    @DisplayName("dos canciones con el mismo titulo conviven en el recorrido")
    void cancionesHomonimas() {
        Cancion unaVersion = new Cancion("Hurt");
        unaVersion.setArtista("Johnny Cash");
        Cancion otraVersion = new Cancion("Hurt");
        otraVersion.setArtista("Nine Inch Nails");

        modo.cargar(List.of(unaVersion, otraVersion));

        assertEquals(2, modo.listaReproduccion().size());
        assertNotNull(modo.siguiente());
        assertNotNull(modo.siguiente());
    }

    @Test
    @DisplayName("un modo sin canciones no deja reproducir")
    void modoVacio() {
        modo.cargar(List.of());

        assertFalse(modo.hayMas());
        assertThrows(NoSuchElementException.class, () -> modo.siguiente());
        assertThrows(NoSuchElementException.class, () -> modo.anterior());
    }

    @Test
    @DisplayName("reiniciar vuelve al comienzo del recorrido")
    void reiniciar() {
        modo.cargar(biblioteca);
        modo.siguiente();
        modo.siguiente();
        modo.siguiente();

        modo.reiniciar();

        assertNull(modo.actual());
        assertEquals("Africa", modo.siguiente().getTitulo());
    }

    @Test
    @DisplayName("expone la altura del arbol y refleja el peor caso")
    void alturaDelArbol() {
        modo.cargar(biblioteca);
        int alturaConEntradaDesordenada = modo.alturaDelArbol();

        // Ahora el peor caso: se cargan ya ordenadas alfabeticamente y el arbol degenera.
        ModoAlfabetico degenerado = new ModoAlfabetico();
        List<Cancion> yaOrdenadas = new ArrayList<>();
        for (String titulo : List.of("A", "B", "C", "D", "E")) {
            yaOrdenadas.add(new Cancion(titulo));
        }
        degenerado.cargar(yaOrdenadas);

        assertEquals(4, degenerado.alturaDelArbol(),
                "insertadas en orden, el arbol se convierte en una lista de altura n-1");
        assertTrue(alturaConEntradaDesordenada < degenerado.alturaDelArbol(),
                "con entrada desordenada el arbol queda mas bajo");
        // Aun degenerado, la navegacion sigue siendo correcta: solo es mas lenta.
        assertEquals("A", degenerado.siguiente().getTitulo());
        assertEquals("B", degenerado.siguiente().getTitulo());
    }
}
