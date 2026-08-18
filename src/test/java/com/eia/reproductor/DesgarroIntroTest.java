package com.eia.reproductor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del desplazamiento de las bandas del desgarro.
 *
 * <p>Es calculo puro, sin ventanas: se puede comprobar sin levantar JavaFX. Y hace falta
 * comprobarlo, porque las tres propiedades que hacen que el efecto se lea como una senial
 * rompiendose —y no como ruido o como que el programa se colgo— no se ven mirando el codigo.</p>
 */
class DesgarroIntroTest {
    private static final int BANDAS = 24;
    private static final double ANCHO = 900;

    /**
     * El tope teorico, tomado de la propia clase.
     *
     * <p>Escrito a mano se quedaria flojo en cuanto se bajara la amplitud: la prueba seguiria
     * pasando sin comprobar nada util.</p>
     */
    private static final double TOPE = IntroDeArranque.AMPLITUD_GLITCH * ANCHO;

    @Test
    @DisplayName("Al final del efecto no queda ninguna banda movida")
    void seAsienta() {
        for (int banda = 0; banda < BANDAS; banda++) {
            assertEquals(0, IntroDeArranque.desplazamientoDeBanda(banda, 99, 1.0, ANCHO), 0.0,
                    "la banda " + banda + " se quedó corrida");
        }
    }

    @Test
    @DisplayName("El temblor decae: la segunda mitad se mueve mucho menos que la primera")
    void decae() {
        double alPrincipio = 0;
        double alFinal = 0;
        for (int cuadro = 0; cuadro < 20; cuadro++) {
            for (int banda = 0; banda < BANDAS; banda++) {
                alPrincipio += Math.abs(
                        IntroDeArranque.desplazamientoDeBanda(banda, cuadro, 0.15, ANCHO));
                alFinal += Math.abs(
                        IntroDeArranque.desplazamientoDeBanda(banda, cuadro, 0.85, ANCHO));
            }
        }
        assertTrue(alFinal < alPrincipio / 4,
                "no se calma: al principio " + alPrincipio + ", al final " + alFinal);
    }

    @Test
    @DisplayName("No se mueven todas las bandas a la vez")
    void noSeMuevenTodas() {
        int movidas = 0;
        int total = 0;
        for (int cuadro = 0; cuadro < 40; cuadro++) {
            for (int banda = 0; banda < BANDAS; banda++) {
                total++;
                if (IntroDeArranque.desplazamientoDeBanda(banda, cuadro, 0.3, ANCHO) != 0) {
                    movidas++;
                }
            }
        }
        double proporcion = (double) movidas / total;
        // Por encima del 85 % se lee como ruido de television en vez de como una señal partiéndose.
        assertTrue(proporcion < 0.85, "se mueven demasiadas: " + proporcion);
        // Y si no se moviera casi ninguna, no habría efecto.
        assertTrue(proporcion > 0.35, "se mueven demasiado pocas: " + proporcion);
    }

    @Test
    @DisplayName("Ninguna banda se sale del área")
    void nadieSeSale() {
        for (int cuadro = 0; cuadro < 60; cuadro++) {
            for (int banda = 0; banda < BANDAS; banda++) {
                for (double avance = 0; avance <= 1; avance += 0.05) {
                    double corrimiento =
                            IntroDeArranque.desplazamientoDeBanda(banda, cuadro, avance, ANCHO);
                    assertTrue(Math.abs(corrimiento) <= TOPE,
                            "banda " + banda + " cuadro " + cuadro + " se fue a " + corrimiento);
                }
            }
        }
    }

    @Test
    @DisplayName("El desgarro es reproducible: mismo arranque, mismo desgarro")
    void reproducible() {
        for (int banda = 0; banda < BANDAS; banda++) {
            for (int cuadro = 0; cuadro < 30; cuadro++) {
                assertEquals(
                        IntroDeArranque.desplazamientoDeBanda(banda, cuadro, 0.4, ANCHO),
                        IntroDeArranque.desplazamientoDeBanda(banda, cuadro, 0.4, ANCHO),
                        0.0);
            }
        }
    }

    @Test
    @DisplayName("Se corre a los dos lados, no siempre al mismo")
    void haciaLosDosLados() {
        boolean haIdoIzquierda = false;
        boolean haIdoDerecha = false;
        for (int cuadro = 0; cuadro < 40 && !(haIdoIzquierda && haIdoDerecha); cuadro++) {
            for (int banda = 0; banda < BANDAS; banda++) {
                double corrimiento =
                        IntroDeArranque.desplazamientoDeBanda(banda, cuadro, 0.3, ANCHO);
                haIdoIzquierda |= corrimiento < 0;
                haIdoDerecha |= corrimiento > 0;
            }
        }
        assertTrue(haIdoIzquierda && haIdoDerecha, "solo se corre hacia un lado");
    }

    @Test
    @DisplayName("La amplitud es proporcional al ancho, no en píxeles fijos")
    void escalaConElAncho() {
        // En una pantalla pequeña el área baja a 826 px y el desgarro tiene que verse igual de
        // fuerte en proporción, no más flojo.
        double enGrande = IntroDeArranque.desplazamientoDeBanda(5, 7, 0.3, 900);
        double enPequenio = IntroDeArranque.desplazamientoDeBanda(5, 7, 0.3, 826);

        assertNotEquals(0, enGrande, "hace falta una banda que sí se mueva para esta prueba");
        assertEquals(enGrande / 900, enPequenio / 826, 1e-9);
    }
}
