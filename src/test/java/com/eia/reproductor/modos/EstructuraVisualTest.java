package com.eia.reproductor.modos;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.EstructuraVisual;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de la descripcion que cada modo hace de su estructura. */
class EstructuraVisualTest {
    private static Cancion cancion(String titulo) {
        return new Cancion(titulo);
    }

    private static List<Cancion> tres() {
        return List.of(cancion("Zombie"), cancion("Africa"), cancion("Creep"));
    }

    @Nested
    @DisplayName("Modo aleatorio → anillo")
    class Anillo {
        @Test
        @DisplayName("Describe todas las canciones cargadas")
        void describeTodas() {
            ModoAleatorio modo = new ModoAleatorio();
            modo.cargar(tres());

            EstructuraVisual.Anillo anillo =
                    (EstructuraVisual.Anillo) modo.estructuraVisual();

            assertEquals(3, anillo.etiquetas().size());
            assertTrue(anillo.etiquetas().containsAll(List.of("Zombie", "Africa", "Creep")));
        }

        @Test
        @DisplayName("Sin reproducir nada, el cursor no apunta a ninguna")
        void sinCursorAlPrincipio() {
            ModoAleatorio modo = new ModoAleatorio();
            modo.cargar(tres());

            assertEquals(-1, ((EstructuraVisual.Anillo) modo.estructuraVisual()).indiceActual());
        }

        @Test
        @DisplayName("Al reproducir, el cursor señala la canción en curso")
        void elCursorSigueALaCancion() {
            ModoAleatorio modo = new ModoAleatorio();
            modo.cargar(tres());
            Cancion sonando = modo.siguiente();

            EstructuraVisual.Anillo anillo =
                    (EstructuraVisual.Anillo) modo.estructuraVisual();

            assertEquals(sonando.getTitulo(), anillo.etiquetas().get(anillo.indiceActual()));
        }

        @Test
        @DisplayName("La lista de etiquetas no se puede modificar desde fuera")
        void etiquetasInmutables() {
            ModoAleatorio modo = new ModoAleatorio();
            modo.cargar(tres());
            List<String> etiquetas = ((EstructuraVisual.Anillo) modo.estructuraVisual())
                    .etiquetas();

            org.junit.jupiter.api.Assertions.assertThrows(
                    UnsupportedOperationException.class, () -> etiquetas.add("colada"));
        }
    }

    @Nested
    @DisplayName("Modo orden de llegada → cola")
    class Cola {
        @Test
        @DisplayName("Muestra lo pendiente en orden de llegada")
        void pendientesEnOrden() {
            ModoOrdenLlegada modo = new ModoOrdenLlegada();
            modo.cargar(tres());

            EstructuraVisual.Cola cola = (EstructuraVisual.Cola) modo.estructuraVisual();

            assertEquals(List.of("Zombie", "Africa", "Creep"), cola.etiquetas());
            assertEquals(0, cola.yaSalieron());
        }

        @Test
        @DisplayName("Al reproducir, la canción sale de la cola y se cuenta")
        void laColaSeVacia() {
            ModoOrdenLlegada modo = new ModoOrdenLlegada();
            modo.cargar(tres());
            modo.siguiente();
            modo.siguiente();

            EstructuraVisual.Cola cola = (EstructuraVisual.Cola) modo.estructuraVisual();

            // Es la prueba de que la cola se vacía de verdad, no que se mueva un índice.
            assertEquals(List.of("Creep"), cola.etiquetas());
            assertEquals(2, cola.yaSalieron());
        }
    }

    @Nested
    @DisplayName("Modo alfabético → árbol")
    class Arbol {
        @Test
        @DisplayName("Un árbol vacío se describe sin raíz")
        void arbolVacio() {
            ModoAlfabetico modo = new ModoAlfabetico();
            modo.cargar(List.of());

            assertNull(((EstructuraVisual.Arbol) modo.estructuraVisual()).raiz());
        }

        @Test
        @DisplayName("Conserva la forma real: la primera insertada es la raíz")
        void conservaLaForma() {
            ModoAlfabetico modo = new ModoAlfabetico();
            // Se insertan en este orden: Manzana primero, así que queda de raíz.
            modo.cargar(List.of(cancion("Manzana"), cancion("Ala"), cancion("Zorro")));

            EstructuraVisual.Rama raiz = ((EstructuraVisual.Arbol) modo.estructuraVisual()).raiz();

            assertEquals("Manzana", raiz.etiqueta());
            assertEquals("Ala", raiz.izquierdo().etiqueta());
            assertEquals("Zorro", raiz.derecho().etiqueta());
            assertEquals(2, raiz.altura());
        }

        @Test
        @DisplayName("Insertar en orden alfabético degenera el árbol en una rama")
        void degeneraSiEntranOrdenadas() {
            ModoAlfabetico modo = new ModoAlfabetico();
            modo.cargar(List.of(cancion("Ala"), cancion("Bota"), cancion("Casa"),
                    cancion("Dedo")));

            EstructuraVisual.Rama raiz = ((EstructuraVisual.Arbol) modo.estructuraVisual()).raiz();

            // El peor caso del que habla el guion de sustentación, comprobado.
            assertEquals(4, raiz.altura(), "cuatro nodos en una sola rama");
            assertNull(raiz.izquierdo(), "no hay nada a la izquierda: todas son mayores");
            assertEquals("Bota", raiz.derecho().etiqueta());
            assertEquals("Casa", raiz.derecho().derecho().etiqueta());
            assertEquals("Dedo", raiz.derecho().derecho().derecho().etiqueta());
        }

        @Test
        @DisplayName("Señala la canción en curso para poder resaltarla")
        void senialaLaActual() {
            ModoAlfabetico modo = new ModoAlfabetico();
            modo.cargar(tres());
            Cancion sonando = modo.siguiente();

            EstructuraVisual.Arbol arbol = (EstructuraVisual.Arbol) modo.estructuraVisual();

            assertEquals(sonando.getTitulo(), arbol.actual());
        }
    }

    @Test
    @DisplayName("Los tres modos describen su estructura con su nombre")
    void losTresSeIdentifican() {
        for (ModoReproduccion modo : List.of(
                new ModoAleatorio(), new ModoOrdenLlegada(), new ModoAlfabetico())) {
            modo.cargar(tres());
            EstructuraVisual visual = modo.estructuraVisual();

            assertNotNull(visual, modo.nombre());
            // El nombre sale de la misma constante que muestra la pestaña, así que no pueden
            // acabar diciendo cosas distintas.
            assertEquals(modo.estructuraUsada(), visual.nombre(), modo.nombre());
        }
    }
}
