package com.eia.reproductor.animacion;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Ecualizador decorativo: unas barras que suben y bajan mientras hay musica.
 *
 * <p>Es la traduccion a JavaFX del bloque HTML/CSS de referencia. Las equivalencias son directas:
 * el {@code display:flex} con {@code align-items:flex-end} es un {@link HBox} alineado abajo, el
 * {@code gap} es el espaciado, y la animacion {@code equalizer} que va de {@code scaleY(0.2)} a
 * {@code scaleY(1)} con {@code alternate} es un {@link Timeline} de ida y vuelta.</p>
 *
 * <p><b>El unico punto delicado es el pivote.</b> En CSS una barra escalada verticalmente crece
 * desde su base porque el contenedor la alinea abajo. En JavaFX el escalado se aplica desde el
 * centro del nodo, asi que una barra crecería hacia los dos lados. Por eso cada barra lleva una
 * transformacion {@link Scale} con el pivote fijado en su borde inferior.</p>
 *
 * <p>Cuando no suena nada las barras se quedan quietas en su altura base, no desaparecen: asi la
 * fila no cambia de tamanio al empezar y parar la musica.</p>
 */
public class BarrasSonido {

    /** Cuantas bandas de frecuencia se piden: una por barra. */
    public static final int BANDAS = AjustesAnimacion.BARRAS_ALTURAS.length;

    /** Cuanto se espera sin recibir espectro antes de volver a la animacion decorativa. */
    private static final long ESPERA_SIN_DATOS_MS = 400;

    /** Fraccion que baja la barra en cada cuadro. Mas bajo, caida mas lenta. */
    private static final double SUAVIZADO_BAJADA = 0.35;

    private final HBox contenedor = new HBox();
    private final List<Timeline> animaciones = new ArrayList<>();
    private final List<Scale> pivotes = new ArrayList<>();
    private boolean sonando;

    /** Verdadero mientras esten llegando niveles reales del audio. */
    private boolean conDatosReales;
    private long ultimoDato;

    /** Construye el ecualizador detenido. */
    public BarrasSonido() {
        contenedor.setAlignment(Pos.BOTTOM_LEFT);
        contenedor.setSpacing(AjustesAnimacion.BARRAS_SEPARACION);
        contenedor.setMinHeight(AjustesAnimacion.BARRAS_ALTO);
        contenedor.setPrefHeight(AjustesAnimacion.BARRAS_ALTO);
        contenedor.setMaxHeight(AjustesAnimacion.BARRAS_ALTO);
        contenedor.setMouseTransparent(true);

        for (int i = 0; i < AjustesAnimacion.BARRAS_ALTURAS.length; i++) {
            double alto = AjustesAnimacion.BARRAS_ALTO * AjustesAnimacion.BARRAS_ALTURAS[i];

            Rectangle barra = new Rectangle(AjustesAnimacion.BARRAS_ANCHO, alto);
            barra.setFill(Color.web(AjustesAnimacion.BARRAS_COLOR));

            // Pivote en la base: sin esto la barra crecería hacia arriba y hacia abajo a la vez.
            Scale pivote = new Scale(1, 1, AjustesAnimacion.BARRAS_ANCHO / 2.0, alto);
            barra.getTransforms().add(pivote);
            pivotes.add(pivote);

            contenedor.getChildren().add(barra);
            animaciones.add(crearAnimacion(pivote, AjustesAnimacion.BARRAS_RETARDOS[i]));
        }
    }

    /** @return el nodo a insertar en la vista */
    public Node nodo() {
        return contenedor;
    }

    /**
     * Arranca o detiene el ecualizador segun este sonando algo.
     *
     * <p>Solo actua cuando el estado cambia de verdad: este metodo se llama en cada refresco de la
     * pantalla y reiniciar las animaciones a cada paso las dejaria congeladas en su primer cuadro.</p>
     *
     * @param hayMusica {@code true} si hay una cancion reproduciendose
     */
    public void sincronizar(boolean hayMusica) {
        if (hayMusica == sonando) {
            return;
        }
        sonando = hayMusica;
        if (hayMusica) {
            // Si ya se esta bailando con el espectro real, la animacion inventada estorba.
            if (!conDatosReales) {
                animaciones.forEach(Timeline::play);
            }
        } else {
            animaciones.forEach(Timeline::stop);
            conDatosReales = false;
            pivotes.forEach(pivote -> pivote.setY(1));
        }
    }

    /**
     * Mueve las barras con el espectro real de la musica.
     *
     * <p>Mientras lleguen niveles, la animacion decorativa se aparta: no tiene sentido inventar un
     * movimiento cuando se conoce el de verdad. Si dejan de llegar —porque la fuente cambio a una
     * que no puede analizar— la animacion vuelve sola pasado {@link #ESPERA_SIN_DATOS_MS}.</p>
     *
     * <p>Se aplica un descenso suave: el espectro salta mucho de un cuadro a otro y sin suavizar
     * las barras parpadean en vez de bailar.</p>
     *
     * @param niveles un valor de 0 a 1 por banda
     */
    public void mostrarNiveles(double[] niveles) {
        if (niveles == null || niveles.length == 0) {
            return;
        }
        if (!conDatosReales) {
            conDatosReales = true;
            animaciones.forEach(Timeline::stop);
        }
        ultimoDato = System.currentTimeMillis();

        for (int i = 0; i < pivotes.size(); i++) {
            double objetivo = Math.max(AjustesAnimacion.BARRAS_ESCALA_MINIMA,
                    niveles[Math.min(i, niveles.length - 1)]);
            Scale pivote = pivotes.get(i);
            // Sube de golpe y baja despacio, como un vumetro: es lo que se lee como "ritmo".
            double suavizado = objetivo > pivote.getY()
                    ? objetivo
                    : pivote.getY() + (objetivo - pivote.getY()) * SUAVIZADO_BAJADA;
            pivote.setY(suavizado);
        }
    }

    /**
     * Devuelve las barras a la animacion decorativa si hace rato que no llegan datos.
     *
     * <p>Lo llama el controlador en cada refresco. Hace falta porque la fuente puede cambiar a una
     * que no analiza —Spotify— y entonces las barras se quedarian congeladas en su ultimo valor.</p>
     */
    public void revisarSiSiguenLlegandoDatos() {
        if (!conDatosReales || System.currentTimeMillis() - ultimoDato < ESPERA_SIN_DATOS_MS) {
            return;
        }
        conDatosReales = false;
        if (sonando) {
            animaciones.forEach(Timeline::play);
        } else {
            pivotes.forEach(pivote -> pivote.setY(1));
        }
    }

    /** Detiene todo. Se llama al cerrar la aplicacion. */
    public void detener() {
        animaciones.forEach(Timeline::stop);
        sonando = false;
        conDatosReales = false;
    }

    private static Timeline crearAnimacion(Scale pivote, double retardoSegundos) {
        Timeline linea = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(pivote.yProperty(), AjustesAnimacion.BARRAS_ESCALA_MINIMA)),
                new KeyFrame(AjustesAnimacion.BARRAS_DURACION,
                        new KeyValue(pivote.yProperty(), 1, Interpolator.EASE_BOTH)));
        linea.setAutoReverse(true);
        linea.setCycleCount(Animation.INDEFINITE);
        // El retardo de cada barra es lo que produce el efecto de ecualizador en vez de que
        // las nueve suban y bajen a la vez.
        linea.setDelay(Duration.seconds(retardoSegundos));
        return linea;
    }
}
