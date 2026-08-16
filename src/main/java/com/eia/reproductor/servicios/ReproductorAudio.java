package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;

import java.util.function.Consumer;

/** Contrato de toda fuente de audio del reproductor. */
public interface ReproductorAudio {
    /** Empieza a reproducir una cancion desde el principio. */
    void reproducir(Cancion cancion);

    /** Detiene la reproduccion conservando la posicion. */
    void pausar();

    /** Continua desde donde se habia pausado. */
    void reanudar();

    /** Detiene la reproduccion y suelta los recursos de la pista actual. */
    void detener();

    /** Salta a una posicion absoluta. */
    void buscarPosicion(long milisegundos);

    /** Salta hacia adelante o hacia atras desde la posicion actual. */
    void avanzarRelativo(long milisegundos);

    /** @return posicion actual dentro de la pista, en milisegundos */
    ReadOnlyLongProperty posicionMsProperty();

    /** @return duracion total de la pista actual, en milisegundos */
    ReadOnlyLongProperty duracionMsProperty();

    /** @return si hay audio sonando en este momento */
    BooleanProperty reproduciendoProperty();

    /** Indica si esta fuente se puede usar. */
    boolean disponible();

    /** Indica si esta fuente sabe reproducir una cancion concreta. */
    boolean puedeReproducir(Cancion cancion);

    /** @return nombre de la fuente, para mostrarlo en la interfaz */
    String nombreFuente();

    /** Define que hacer cuando una pista llega al final. */
    void setAlTerminarPista(Runnable callback);

    /** Define a donde mandar los problemas de reproduccion. */
    default void setAlFallar(Consumer<String> callback) {
        // Sin implementacion a proposito.
    }

    /** Ajusta el volumen de la fuente. */
    default void setVolumen(int porcentaje) {
        // Sin implementacion a proposito.
    }

    /** Pide que se avise del espectro de la musica mientras suena. */
    default void setAlAnalizarEspectro(Consumer<double[]> oyente, int bandas) {
        // Sin implementacion a proposito.
    }

    /** @return {@code true} si esta fuente puede entregar el espectro de lo que suena */
    default boolean analizaEspectro() {
        return false;
    }

    /** Indica si la fuente necesita conexion a internet para sonar. */
    default boolean requiereRed() {
        return false;
    }

    /** Pide que se eviten las fuentes que dependen de la red. */
    default void setEvitarRed(boolean evitar) {
        // Sin implementacion a proposito.
    }
}
