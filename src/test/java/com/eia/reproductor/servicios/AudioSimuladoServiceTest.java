package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Pruebas de la fuente de audio simulada. */
class AudioSimuladoServiceTest {
    private static final long UN_MINUTO_MS = 60_000;

    private AudioSimuladoService servicio;

    @BeforeEach
    void prepararEntorno() {
        assumeTrue(EntornoJavaFx.disponible(), "No hay entorno grafico para levantar JavaFX.");
        EntornoJavaFx.enElHiloFx(() -> servicio = new AudioSimuladoService());
    }

    private static Cancion deDuracion(int segundos) {
        Cancion cancion = new Cancion("Titulo");
        cancion.setDuracionSegundos(segundos);
        return cancion;
    }

    @Test
    @DisplayName("Al reproducir toma la duracion declarada por la cancion")
    void tomaLaDuracionDeLaCancion() {
        EntornoJavaFx.enElHiloFx(() -> servicio.reproducir(deDuracion(60)));

        assertEquals(UN_MINUTO_MS, servicio.duracionMsProperty().get());
        assertEquals(0, servicio.posicionMsProperty().get());
        assertTrue(servicio.reproduciendoProperty().get());
    }

    @Test
    @DisplayName("Sin duracion declarada usa la de respaldo, no cero")
    void usaLaDuracionDeRespaldo() {
        EntornoJavaFx.enElHiloFx(() -> servicio.reproducir(new Cancion("Sin duracion")));

        // Cero dejaria la barra de progreso inservible y la pista no avanzaria nunca.
        assertTrue(servicio.duracionMsProperty().get() > 0);
    }

    @Test
    @DisplayName("La posicion avanza sola con el tiempo")
    void laPosicionAvanzaSola() throws InterruptedException {
        EntornoJavaFx.enElHiloFx(() -> servicio.reproducir(deDuracion(300)));

        CountDownLatch avanzo = new CountDownLatch(1);
        servicio.posicionMsProperty().addListener((observable, anterior, actual) -> {
            if (actual.longValue() > 0) {
                avanzo.countDown();
            }
        });

        EntornoJavaFx.esperar(avanzo);
        assertTrue(servicio.posicionMsProperty().get() > 0);
    }

    @Test
    @DisplayName("Al llegar al final avisa una sola vez y se detiene")
    void avisaAlTerminarLaPista() {
        CountDownLatch termino = new CountDownLatch(1);
        EntornoJavaFx.enElHiloFx(() -> {
            servicio.setAlTerminarPista(termino::countDown);
            // Una pista de un segundo termina enseguida sin volver lenta la prueba.
            servicio.reproducir(deDuracion(1));
        });

        EntornoJavaFx.esperar(termino);
        assertFalse(servicio.reproduciendoProperty().get());
        assertEquals(servicio.duracionMsProperty().get(), servicio.posicionMsProperty().get());
    }

    @Test
    @DisplayName("Pausar congela la posicion y reanudar la retoma")
    void pausarYReanudar() {
        EntornoJavaFx.enElHiloFx(() -> {
            servicio.reproducir(deDuracion(300));
            servicio.buscarPosicion(30_000);
            servicio.pausar();
        });

        assertFalse(servicio.reproduciendoProperty().get());
        assertEquals(30_000, servicio.posicionMsProperty().get());

        EntornoJavaFx.enElHiloFx(servicio::reanudar);
        assertTrue(servicio.reproduciendoProperty().get());
        assertTrue(servicio.posicionMsProperty().get() >= 30_000, "No debe volver al principio");
    }

    @Test
    @DisplayName("Buscar fuera de la pista se recorta a los extremos")
    void buscarSeRecortaALosExtremos() {
        EntornoJavaFx.enElHiloFx(() -> {
            servicio.reproducir(deDuracion(60));
            servicio.pausar();

            servicio.buscarPosicion(-5_000);
            assertEquals(0, servicio.posicionMsProperty().get());

            servicio.buscarPosicion(UN_MINUTO_MS * 10);
            assertEquals(UN_MINUTO_MS, servicio.posicionMsProperty().get());
        });
    }

    @Test
    @DisplayName("Avanzar en relativo suma sobre la posicion actual y respeta los limites")
    void avanzarRelativoRespetaLosLimites() {
        EntornoJavaFx.enElHiloFx(() -> {
            servicio.reproducir(deDuracion(60));
            servicio.pausar();

            servicio.buscarPosicion(10_000);
            servicio.avanzarRelativo(5_000);
            assertEquals(15_000, servicio.posicionMsProperty().get());

            servicio.avanzarRelativo(-50_000);
            assertEquals(0, servicio.posicionMsProperty().get());
        });
    }

    @Test
    @DisplayName("Detener deja la fuente en blanco")
    void detenerDejaLaFuenteEnBlanco() {
        EntornoJavaFx.enElHiloFx(() -> {
            servicio.reproducir(deDuracion(60));
            servicio.detener();
        });

        assertFalse(servicio.reproduciendoProperty().get());
        assertEquals(0, servicio.posicionMsProperty().get());
    }

    @Test
    @DisplayName("Acepta cualquier cancion: es la fuente de ultimo recurso")
    void aceptaCualquierCancion() {
        assertTrue(servicio.disponible());
        assertTrue(servicio.puedeReproducir(new Cancion("Cualquiera")));
        assertFalse(servicio.puedeReproducir(null));
    }
}
