package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.util.Duration;

/**
 * Fuente de audio de ultimo recurso: no suena nada, pero el reproductor se comporta igual.
 *
 * <p>Se usa con las canciones que solo tienen metadata, sin archivo local ni URI de Spotify. Un
 * reloj interno avanza la posicion al mismo ritmo que lo haria la reproduccion real y avisa
 * cuando la pista "termina", de modo que la barra de progreso, el encadenado con la siguiente
 * cancion y el resto de la interfaz funcionan sin ningun caso especial.</p>
 *
 * <p>Que exista esta implementacion es lo que permite que el reproductor nunca se quede sin
 * fuente: {@link AudioRuteado} siempre encuentra al menos esta.</p>
 */
public class AudioSimuladoService implements ReproductorAudio {

    /** Cada cuanto avanza el reloj. 250 ms da una barra fluida sin gastar CPU. */
    private static final Duration PASO = Duration.millis(250);

    /** Duracion que se asume cuando la cancion no declara la suya. */
    private static final long DURACION_POR_DEFECTO_MS = 180_000;

    private final ReadOnlyLongWrapper posicionMs = new ReadOnlyLongWrapper(0);
    private final ReadOnlyLongWrapper duracionMs = new ReadOnlyLongWrapper(0);
    private final BooleanProperty reproduciendo = new SimpleBooleanProperty(false);

    private Timeline reloj;
    private Runnable alTerminarPista;

    @Override
    public void reproducir(Cancion cancion) {
        detener();
        duracionMs.set(duracionDe(cancion));
        posicionMs.set(0);
        reanudar();
    }

    @Override
    public void pausar() {
        if (reloj != null) {
            reloj.pause();
        }
        reproduciendo.set(false);
    }

    @Override
    public void reanudar() {
        if (duracionMs.get() <= 0) {
            return;
        }
        if (reloj == null) {
            reloj = new Timeline(new KeyFrame(PASO, evento -> avanzarUnPaso()));
            reloj.setCycleCount(Animation.INDEFINITE);
        }
        reloj.play();
        reproduciendo.set(true);
    }

    @Override
    public void detener() {
        if (reloj != null) {
            reloj.stop();
            reloj = null;
        }
        reproduciendo.set(false);
        posicionMs.set(0);
    }

    @Override
    public void buscarPosicion(long milisegundos) {
        long limitada = Math.max(0, Math.min(milisegundos, duracionMs.get()));
        posicionMs.set(limitada);
    }

    @Override
    public void avanzarRelativo(long milisegundos) {
        buscarPosicion(posicionMs.get() + milisegundos);
    }

    @Override
    public ReadOnlyLongProperty posicionMsProperty() {
        return posicionMs.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyLongProperty duracionMsProperty() {
        return duracionMs.getReadOnlyProperty();
    }

    @Override
    public BooleanProperty reproduciendoProperty() {
        return reproduciendo;
    }

    @Override
    public boolean disponible() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Acepta cualquier cancion: es la fuente de respaldo y por eso va siempre la ultima en el
     * orden de preferencia del enrutador.</p>
     */
    @Override
    public boolean puedeReproducir(Cancion cancion) {
        return cancion != null;
    }

    @Override
    public String nombreFuente() {
        return "Simulado";
    }

    @Override
    public void setAlTerminarPista(Runnable callback) {
        this.alTerminarPista = callback;
    }

    private void avanzarUnPaso() {
        long siguiente = posicionMs.get() + (long) PASO.toMillis();
        if (siguiente >= duracionMs.get()) {
            posicionMs.set(duracionMs.get());
            pausar();
            if (alTerminarPista != null) {
                alTerminarPista.run();
            }
            return;
        }
        posicionMs.set(siguiente);
    }

    private static long duracionDe(Cancion cancion) {
        if (cancion == null || cancion.getDuracionSegundos() <= 0) {
            return DURACION_POR_DEFECTO_MS;
        }
        return cancion.getDuracionSegundos() * 1000L;
    }
}
