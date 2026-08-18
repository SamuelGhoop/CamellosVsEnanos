package com.eia.reproductor;

import com.eia.reproductor.servicios.EntornoJavaFx;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pruebas de la presentacion de arranque.
 *
 * <p><b>Por que existe.</b> La presentacion se monta con animaciones que solo fallan al
 * ejecutarse: la primera version usaba un {@code Interpolator.SPLINE} con un punto de control
 * fuera de rango y reventaba con {@code IllegalArgumentException} en cuanto arrancaba —o sea, al
 * abrir la aplicacion, y solo en la maquina del usuario—. Compilaba perfectamente.</p>
 *
 * <p>Tambien vigila lo que no se puede dejar al azar: que <b>siempre</b> avise al terminar. Si no
 * lo hiciera, la ventana principal no se mostraria nunca y la aplicacion quedaria colgada en la
 * pantalla de carga.</p>
 */
class IntroDeArranqueTest {
    /** Margen sobre la duracion nominal, por si la maquina va cargada. */
    private static final int ESPERA_SEGUNDOS = 25;

    @BeforeAll
    static void prepararEntorno() {
        assumeTrue(EntornoJavaFx.disponible(), "No hay entorno gráfico para levantar JavaFX.");
    }

    @Test
    @DisplayName("Corre entera sin reventar y avisa al terminar")
    void terminaSolaYAvisa() throws InterruptedException {
        AtomicReference<Throwable> fallo = new AtomicReference<>();
        CountDownLatch termino = new CountDownLatch(1);

        // Las animaciones se ejecutan en el hilo de JavaFX y sus excepciones no vuelven al hilo de
        // la prueba: hay que recogerlas con un manejador propio o pasarian desapercibidas.
        Platform.runLater(() -> {
            Thread.currentThread().setUncaughtExceptionHandler(
                    (hilo, excepcion) -> fallo.set(excepcion));
            App.cargarFuentePixel();
            IntroDeArranque intro = new IntroDeArranque();
            intro.alTerminar(termino::countDown);
            intro.mostrar();
            intro.permitirArranque();
        });

        assertTrue(termino.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS),
                "la presentación no terminó sola: la aplicación se quedaría en la pantalla de carga");
        assertNull(fallo.get(), () -> "la presentación lanzó " + fallo.get());
    }

    @Test
    @DisplayName("Saltarla avisa igual, y una sola vez")
    void saltarlaAvisaUnaVez() throws InterruptedException {
        CountDownLatch termino = new CountDownLatch(1);
        int[] avisos = {0};

        Platform.runLater(() -> {
            App.cargarFuentePixel();
            IntroDeArranque intro = new IntroDeArranque();
            intro.alTerminar(() -> {
                avisos[0]++;
                termino.countDown();
            });
            intro.mostrar();
            intro.permitirArranque();
            intro.saltar();
            // Dos veces seguidas: un clic mientras se cierra no puede abrir dos ventanas.
            intro.saltar();
        });

        assertTrue(termino.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS), "saltarla no avisó");
        EntornoJavaFx.enElHiloFx(() -> { });
        org.junit.jupiter.api.Assertions.assertEquals(1, avisos[0],
                "avisó más de una vez: la ventana principal se mostraría dos veces");
    }

    @Test
    @DisplayName("Si se pide el aviso después de haber terminado, se atiende igual")
    void avisaAunqueSeRegistreTarde() throws InterruptedException {
        CountDownLatch termino = new CountDownLatch(1);

        // Es el caso real: App la salta ante un fallo al montar la ventana, y solo despues
        // registra que hacer al terminar.
        Platform.runLater(() -> {
            App.cargarFuentePixel();
            IntroDeArranque intro = new IntroDeArranque();
            intro.mostrar();
            intro.permitirArranque();
            intro.saltar();
            intro.alTerminar(termino::countDown);
        });

        assertTrue(termino.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS),
                "registrarse tarde se quedó sin aviso");
    }
}
