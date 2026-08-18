package com.eia.reproductor;

import com.eia.reproductor.IntroDeArranque.Movimiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los momentos fotografiables de la presentacion.
 *
 * <p>Los instantes se derivan del ritmo base, asi que si manana un acto cambia de duracion se
 * recolocan solos. Lo que estas pruebas vigilan es que sigan cumpliendo lo que los hace utiles:
 * ir en orden y apuntar al medio de cada movimiento, no a su borde.</p>
 */
class MovimientoIntroTest {

    @Test
    @DisplayName("Hay un momento por cada beat del guion")
    void unoPorBeat() {
        assertEquals(5, Movimiento.values().length);
    }

    @Test
    @DisplayName("Los instantes van en orden estricto")
    void enOrden() {
        Movimiento[] momentos = Movimiento.values();
        for (int i = 1; i < momentos.length; i++) {
            assertTrue(momentos[i - 1].instante() < momentos[i].instante(),
                    momentos[i - 1] + " no va antes que " + momentos[i]);
        }
    }

    @Test
    @DisplayName("Ninguno cae en 0 ni en 1: son el medio de un movimiento, no un borde")
    void ningunoEnLosBordes() {
        for (Movimiento momento : Movimiento.values()) {
            assertTrue(momento.instante() > 0, momento + " cae en el arranque");
            assertTrue(momento.instante() < 1, momento + " cae en el final");
        }
    }

    @Test
    @DisplayName("Cada momento cae dentro del tramo que dice representar")
    void cadaUnoEnSuTramo() {
        double total = IntroDeArranque.BASE_TOTAL;
        double finActoUno = IntroDeArranque.BASE_ACTO_UNO / total;
        double finGlitch = finActoUno + IntroDeArranque.BASE_GLITCH / total;
        double finActoDos = finGlitch + IntroDeArranque.BASE_ACTO_DOS / total;
        double finActoTres = finActoDos + IntroDeArranque.BASE_ACTO_TRES / total;

        assertEnTramo(Movimiento.BAJADA, 0, finActoUno);
        assertEnTramo(Movimiento.DESGARRO, finActoUno, finGlitch);
        assertEnTramo(Movimiento.IMPACTO, finGlitch, finActoDos);
        assertEnTramo(Movimiento.CARGA, finActoDos, finActoTres);
        assertEnTramo(Movimiento.FUNDIDO, finActoTres, 1);
    }

    private static void assertEnTramo(Movimiento momento, double desde, double hasta) {
        assertTrue(momento.instante() > desde && momento.instante() < hasta,
                momento + " en " + momento.instante() + " se sale de [" + desde + ", " + hasta + "]");
    }

    @Test
    @DisplayName("Todos tienen etiqueta y no se repiten")
    void etiquetasUtiles() {
        Set<String> vistas = new HashSet<>();
        for (Movimiento momento : Movimiento.values()) {
            assertFalse(momento.etiqueta().isBlank(), momento + " sin etiqueta");
            assertTrue(vistas.add(momento.etiqueta()), "etiqueta repetida: " + momento.etiqueta());
        }
    }
}
