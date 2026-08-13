package com.eia.reproductor.modos;

import com.eia.reproductor.modelo.Cancion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del {@link ModoAleatorio}: circularidad infinita en ambos sentidos. */
@DisplayName("Modo 1 - Aleatorio (lista circular doble)")
class ModoAleatorioTest {

    private ModoAleatorio modo;
    private List<Cancion> biblioteca;

    @BeforeEach
    void preparar() {
        // Semilla fija para que el barajado sea reproducible.
        modo = new ModoAleatorio(new Random(2026));
        biblioteca = new ArrayList<>();
        for (String titulo : List.of("Alfa", "Bravo", "Charlie", "Delta", "Echo")) {
            biblioteca.add(new Cancion(titulo));
        }
    }

    @Test
    @DisplayName("identifica su nombre y su estructura")
    void identidad() {
        assertEquals("Aleatorio", modo.nombre());
        assertEquals("Lista Ligada Circular Doble", modo.estructuraUsada());
        assertTrue(modo.permiteAnterior());
    }

    @Test
    @DisplayName("al cargar no hay nada sonando todavia")
    void cargaSinReproducir() {
        modo.cargar(biblioteca);

        assertNull(modo.actual());
        assertTrue(modo.hayMas());
        assertEquals(5, modo.listaReproduccion().size());
    }

    @Test
    @DisplayName("la primera llamada a siguiente() arranca por la primera del orden barajado")
    void primeraLlamadaArranca() {
        modo.cargar(biblioteca);
        Cancion primeraDelOrden = modo.listaReproduccion().get(0);

        assertEquals(primeraDelOrden, modo.siguiente());
        assertEquals(primeraDelOrden, modo.actual());
    }

    @Test
    @DisplayName("nunca existe un final: tras la ultima vuelve la primera")
    void reproduccionInfinita() {
        modo.cargar(biblioteca);
        List<Cancion> orden = modo.listaReproduccion();

        // Una vuelta completa.
        for (Cancion esperada : orden) {
            assertEquals(esperada, modo.siguiente());
        }
        // Y la siguiente es de nuevo la primera, sin excepcion ni null.
        assertEquals(orden.get(0), modo.siguiente());
        assertTrue(modo.hayMas());
    }

    @Test
    @DisplayName("tres vueltas seguidas siempre devuelven canciones validas")
    void tresVueltasCompletas() {
        modo.cargar(biblioteca);

        for (int i = 0; i < 5 * 3; i++) {
            assertNotNull(modo.siguiente(), "la reproduccion circular nunca debe agotarse");
        }
        assertTrue(modo.hayMas());
    }

    @Test
    @DisplayName("se puede retroceder y el recorrido es simetrico")
    void retrocesoSimetrico() {
        modo.cargar(biblioteca);
        modo.siguiente();
        Cancion puntoDePartida = modo.actual();

        modo.siguiente();
        modo.siguiente();
        modo.anterior();
        modo.anterior();

        assertEquals(puntoDePartida, modo.actual());
    }

    @Test
    @DisplayName("retroceder desde la primera lleva a la ultima")
    void retrocederDaLaVuelta() {
        modo.cargar(biblioteca);
        List<Cancion> orden = modo.listaReproduccion();
        modo.siguiente();

        assertEquals(orden.get(orden.size() - 1), modo.anterior());
    }

    @Test
    @DisplayName("con una sola cancion, avanzar y retroceder devuelven siempre esa cancion")
    void unaSolaCancion() {
        Cancion unica = new Cancion("Unica");
        modo.cargar(List.of(unica));

        assertEquals(unica, modo.siguiente());
        assertEquals(unica, modo.siguiente());
        assertEquals(unica, modo.anterior());
    }

    @Test
    @DisplayName("volver a mezclar cambia el orden pero no interrumpe lo que suena")
    void volverAMezclar() {
        for (int i = 0; i < 20; i++) {
            biblioteca.add(new Cancion("Extra " + i));
        }
        modo.cargar(biblioteca);
        modo.siguiente();
        modo.siguiente();
        Cancion sonando = modo.actual();
        List<Cancion> ordenAnterior = modo.listaReproduccion();

        modo.volverAMezclar();

        assertEquals(sonando, modo.actual(), "la cancion en curso no debe cambiar");
        assertFalse(ordenAnterior.equals(modo.listaReproduccion()), "el orden deberia haber cambiado");
        assertEquals(ordenAnterior.size(), modo.listaReproduccion().size());
    }

    @Test
    @DisplayName("agregar incorpora la cancion al recorrido")
    void agregar() {
        modo.cargar(biblioteca);
        Cancion nueva = new Cancion("Foxtrot");

        modo.agregar(nueva);

        assertEquals(6, modo.listaReproduccion().size());
        assertTrue(modo.listaReproduccion().contains(nueva));
    }

    @Test
    @DisplayName("agregar sobre un modo vacio lo deja reproducible")
    void agregarSobreModoVacio() {
        modo.cargar(List.of());
        assertFalse(modo.hayMas());

        Cancion nueva = new Cancion("Primera");
        modo.agregar(nueva);

        assertTrue(modo.hayMas());
        assertEquals(nueva, modo.siguiente());
    }

    @Test
    @DisplayName("eliminar la cancion que suena reposiciona la navegacion")
    void eliminarLaQueSuena() {
        modo.cargar(biblioteca);
        modo.siguiente();
        Cancion sonando = modo.actual();

        modo.eliminar(sonando);

        assertEquals(4, modo.listaReproduccion().size());
        assertFalse(modo.listaReproduccion().contains(sonando));
        assertNotNull(modo.actual(), "debe quedar posicionado en otra cancion");
        // Y la navegacion tiene que seguir viva.
        assertNotNull(modo.siguiente());
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
        assertTrue(modo.listaReproduccion().isEmpty());
    }

    @Test
    @DisplayName("eliminar una cancion que no esta no altera nada")
    void eliminarInexistente() {
        modo.cargar(biblioteca);

        modo.eliminar(new Cancion("No existe"));

        assertEquals(5, modo.listaReproduccion().size());
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
    @DisplayName("reiniciar devuelve la reproduccion al comienzo")
    void reiniciar() {
        modo.cargar(biblioteca);
        Cancion primera = modo.siguiente();
        modo.siguiente();
        modo.siguiente();

        modo.reiniciar();

        assertNull(modo.actual());
        assertTrue(modo.historial().isEmpty());
        assertEquals(primera, modo.siguiente());
    }

    @Test
    @DisplayName("el historial registra lo reproducido en orden")
    void historial() {
        modo.cargar(biblioteca);
        Cancion uno = modo.siguiente();
        Cancion dos = modo.siguiente();

        assertEquals(List.of(uno, dos), modo.historial());
    }

    @Test
    @DisplayName("el historial no crece sin limite")
    void historialAcotado() {
        modo.cargar(biblioteca);
        for (int i = 0; i < ModoBase.MAX_HISTORIAL + 30; i++) {
            modo.siguiente();
        }

        assertEquals(ModoBase.MAX_HISTORIAL, modo.historial().size());
    }
}
