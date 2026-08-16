package com.eia.reproductor.servicios;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Arranca el entorno de JavaFX una sola vez para las pruebas que lo necesitan. */
public final class EntornoJavaFx {
    private static final long ESPERA_MAXIMA_SEGUNDOS = 15;

    private static boolean iniciado;
    private static boolean disponible;

    private EntornoJavaFx() {
    }

    /** @return {@code true} si el hilo de JavaFX esta levantado y se puede usar */
    public static synchronized boolean disponible() {
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

    /** Ejecuta una accion en el hilo de JavaFX y espera a que termine. */
    public static void enElHiloFx(Runnable accion) {
        AtomicReference<Throwable> fallo = new AtomicReference<>();
        CountDownLatch terminado = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                accion.run();
            } catch (Throwable excepcion) {
                fallo.set(excepcion);
            } finally {
                terminado.countDown();
            }
        });
        esperar(terminado);
        relanzar(fallo.get());
    }

    /** Relanza el fallo en el hilo de la prueba conservando su tipo, para que JUnit lo entienda. */
    private static void relanzar(Throwable fallo) {
        switch (fallo) {
            case null -> { }
            case RuntimeException excepcion -> throw excepcion;
            case Error error -> throw error;
            default -> throw new AssertionError("Falló dentro del hilo de JavaFX.", fallo);
        }
    }

    /** Espera a que se abra un cerrojo, fallando si tarda demasiado. */
    public static void esperar(CountDownLatch cerrojo) {
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
