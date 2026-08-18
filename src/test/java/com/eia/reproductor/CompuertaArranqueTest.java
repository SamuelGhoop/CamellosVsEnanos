package com.eia.reproductor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pruebas de la compuerta que decide cuando mostrar la ventana principal.
 *
 * <p>Cubre el fallo que dejaba la aplicacion a medio abrir: al saltar la presentacion antes de que
 * la ventana estuviera montada, se llamaba a {@code show()} sobre un escenario sin escena y el
 * montaje posterior reventaba con "Cannot set style once stage has been set visible".</p>
 */
class CompuertaArranqueTest {

    /** La compuerta es privada porque nadie mas la necesita; la prueba llega por reflexion. */
    private static Object nuevaCompuerta(Runnable alAbrirse) throws Exception {
        Class<?> clase = Class.forName("com.eia.reproductor.App$Compuerta");
        Constructor<?> constructor = clase.getDeclaredConstructor(Runnable.class);
        constructor.setAccessible(true);
        return constructor.newInstance(alAbrirse);
    }

    private static void avisar(Object compuerta, String metodo) throws Exception {
        Method m = compuerta.getClass().getDeclaredMethod(metodo);
        m.setAccessible(true);
        m.invoke(compuerta);
    }

    @Test
    @DisplayName("Con una sola condición no abre")
    void unaSolaNoAbre() throws Exception {
        int[] veces = {0};
        Object compuerta = nuevaCompuerta(() -> veces[0]++);

        avisar(compuerta, "presentacionTerminada");
        assertEquals(0, veces[0], "abrió sin la ventana montada");

        Object otra = nuevaCompuerta(() -> veces[0]++);
        avisar(otra, "ventanaMontada");
        assertEquals(0, veces[0], "abrió sin haber terminado la presentación");
    }

    @Test
    @DisplayName("Abre con las dos, saltando la intro antes de que monte la ventana")
    void saltarAntesDeMontar() throws Exception {
        int[] veces = {0};
        Object compuerta = nuevaCompuerta(() -> veces[0]++);

        // El orden que rompía: primero el salto, después el montaje.
        avisar(compuerta, "presentacionTerminada");
        avisar(compuerta, "ventanaMontada");

        assertEquals(1, veces[0]);
    }

    @Test
    @DisplayName("Abre con las dos en el orden normal")
    void ordenNormal() throws Exception {
        int[] veces = {0};
        Object compuerta = nuevaCompuerta(() -> veces[0]++);

        avisar(compuerta, "ventanaMontada");
        avisar(compuerta, "presentacionTerminada");

        assertEquals(1, veces[0]);
    }

    @Test
    @DisplayName("Avisos repetidos no muestran la ventana dos veces")
    void abreUnaSolaVez() throws Exception {
        int[] veces = {0};
        Object compuerta = nuevaCompuerta(() -> veces[0]++);

        avisar(compuerta, "ventanaMontada");
        avisar(compuerta, "presentacionTerminada");
        avisar(compuerta, "presentacionTerminada");
        avisar(compuerta, "ventanaMontada");

        assertEquals(1, veces[0]);
    }
}
