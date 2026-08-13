package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;

import java.util.function.Consumer;

/**
 * Contrato de toda fuente de audio del reproductor.
 *
 * <p>Es el ejemplo mas fuerte de polimorfismo del proyecto: el controlador guarda una referencia
 * de este tipo y jamas sabe si detras hay un MP3 local, un reloj simulado o Spotify. Cambiar de
 * fuente no requiere tocar ni la interfaz grafica ni las estructuras de datos.</p>
 *
 * <p><b>Sobre las dependencias de JavaFX.</b> Este paquete tiene prohibido importar
 * {@code javafx.scene.*}, y se respeta: aqui solo se usan propiedades observables
 * ({@code javafx.beans.property}), que son un mecanismo de notificacion, no interfaz grafica.
 * Gracias a ellas la barra de progreso se ata al avance de la reproduccion sin que la fuente de
 * audio sepa que existe una barra. La unica clase que rompe la regla es la implementacion local,
 * porque {@code MediaPlayer} vive en {@code javafx.scene.media} y no hay alternativa.</p>
 *
 * @see AudioLocalService
 * @see AudioSimuladoService
 * @see AudioRuteado
 */
public interface ReproductorAudio {

    /**
     * Empieza a reproducir una cancion desde el principio.
     *
     * @param cancion cancion a reproducir
     */
    void reproducir(Cancion cancion);

    /** Detiene la reproduccion conservando la posicion. */
    void pausar();

    /** Continua desde donde se habia pausado. */
    void reanudar();

    /** Detiene la reproduccion y suelta los recursos de la pista actual. */
    void detener();

    /**
     * Salta a una posicion absoluta.
     *
     * @param milisegundos posicion desde el inicio de la pista
     */
    void buscarPosicion(long milisegundos);

    /**
     * Salta hacia adelante o hacia atras desde la posicion actual.
     *
     * @param milisegundos desplazamiento; negativo para retroceder
     */
    void avanzarRelativo(long milisegundos);

    /** @return posicion actual dentro de la pista, en milisegundos */
    ReadOnlyLongProperty posicionMsProperty();

    /** @return duracion total de la pista actual, en milisegundos */
    ReadOnlyLongProperty duracionMsProperty();

    /** @return si hay audio sonando en este momento */
    BooleanProperty reproduciendoProperty();

    /**
     * Indica si esta fuente se puede usar.
     *
     * <p>Para el audio local siempre es cierto; para Spotify dependera de que el proceso externo
     * haya arrancado y las credenciales existan.</p>
     *
     * @return {@code true} si la fuente esta operativa
     */
    boolean disponible();

    /**
     * Indica si esta fuente sabe reproducir una cancion concreta.
     *
     * <p>Esta consulta es la que permite que agregar una fuente nueva no obligue a tocar el
     * controlador: {@link AudioRuteado} pregunta a cada fuente y elige la primera que sepa. Sin
     * ella, el controlador tendria que conocer los tipos concretos y decidir el mismo.</p>
     *
     * @param cancion cancion candidata
     * @return {@code true} si puede reproducirla
     */
    boolean puedeReproducir(Cancion cancion);

    /** @return nombre de la fuente, para mostrarlo en la interfaz */
    String nombreFuente();

    /**
     * Define que hacer cuando una pista llega al final.
     *
     * <p>Lo usa el controlador para encadenar con {@code siguiente()} del modo activo: las
     * estructuras de datos siguen mandando el orden y la fuente de audio solo avisa que termino.</p>
     *
     * @param callback accion a ejecutar al terminar la pista
     */
    void setAlTerminarPista(Runnable callback);

    /**
     * Define a donde mandar los problemas de reproduccion.
     *
     * <p>Es un aviso, no una excepcion, porque los fallos de audio llegan tarde y en otro hilo: un
     * MP3 corrupto no revienta al abrirlo sino cuando el decodificador se atraganta. Lanzar una
     * excepcion en ese momento no la podria capturar nadie.</p>
     *
     * <p>Por defecto no hace nada: una fuente que no puede fallar no tiene nada que avisar.</p>
     *
     * @param callback recibe el mensaje para mostrar al usuario
     */
    default void setAlFallar(Consumer<String> callback) {
        // Sin implementacion a proposito.
    }
}
