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

    private final HBox contenedor = new HBox();
    private final List<Timeline> animaciones = new ArrayList<>();
    private final List<Scale> pivotes = new ArrayList<>();
    private boolean sonando;

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
            animaciones.forEach(Timeline::play);
        } else {
            animaciones.forEach(Timeline::stop);
            pivotes.forEach(pivote -> pivote.setY(1));
        }
    }

    /** Detiene todo. Se llama al cerrar la aplicacion. */
    public void detener() {
        animaciones.forEach(Timeline::stop);
        sonando = false;
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
