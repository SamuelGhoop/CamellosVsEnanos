package com.eia.reproductor.servicios;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Arranca el entorno de JavaFX una sola vez para las pruebas que lo necesitan.
 *
 * <p>Las clases que usan {@code Timeline} no pueden probarse sin el hilo de JavaFX: el reloj de las
 * animaciones lo administra el propio entorno grafico. Este ayudante lo levanta una vez por
 * ejecucion y ofrece un metodo para correr codigo en ese hilo y esperar el resultado.</p>
 *
 * <p>Si la maquina no puede levantarlo (una consola sin escritorio, por ejemplo),
 * {@link #disponible()} devuelve {@code false} y las pruebas que dependen de el se saltan en vez de
 * fallar: no es la aplicacion la que esta rota, es que el entorno no da.</p>
 */
final class EntornoJavaFx {

    private static final long ESPERA_MAXIMA_SEGUNDOS = 15;

    private static boolean iniciado;
    private static boolean disponible;

    private EntornoJavaFx() {
    }

    /** @return {@code true} si el hilo de JavaFX esta levantado y se puede usar */
    static synchronized boolean disponible() {
        if (iniciado) {
            return disponible;
        }
        iniciado = true;
        try {
            CountDownLatch listo = new CountDownLatch(1);
            Platform.startup(listo::countDown);
            disponible = listo.await(ESPERA_MAXIMA_SEGUNDOS, TimeUnit.SECONDS);
        } catch (IllegalStateException yaEstabaArrancado) {
            disponible = true;
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            disponible = false;
        } catch (RuntimeException | Error sinEntornoGrafico) {
            disponible = false;
        }
        if (disponible) {
            // Sin esto, terminar la ultima prueba dejaria vivo el hilo de JavaFX y la JVM de
            // Surefire no cerraria.
            Platform.setImplicitExit(false);
        }
        return disponible;
    }

    /**
     * Ejecuta una accion en el hilo de JavaFX y espera a que termine.
     *
     * @param accion codigo a ejecutar
     */
    static void enElHiloFx(Runnable accion) {
        AtomicReference<RuntimeException> fallo = new AtomicReference<>();
        CountDownLatch terminado = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                accion.run();
            } catch (RuntimeException excepcion) {
                fallo.set(excepcion);
            } finally {
                terminado.countDown();
            }
        });
        esperar(terminado);
        if (fallo.get() != null) {
            throw fallo.get();
        }
    }

    /**
     * Espera a que se abra un cerrojo, fallando si tarda demasiado.
     *
     * @param cerrojo cerrojo a esperar
     */
    static void esperar(CountDownLatch cerrojo) {
        try {
            if (!cerrojo.await(ESPERA_MAXIMA_SEGUNDOS, TimeUnit.SECONDS)) {
                throw new AssertionError("El hilo de JavaFX no respondió a tiempo.");
            }
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Espera interrumpida.", interrupcion);
        }
    }
}
