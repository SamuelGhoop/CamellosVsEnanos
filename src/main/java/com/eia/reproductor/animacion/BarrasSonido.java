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

/** Ecualizador decorativo: unas barras que suben y bajan mientras hay musica. */
public class BarrasSonido {
    /** Cuantas bandas de frecuencia se piden: una por barra. */
    public static final int BANDAS = AjustesAnimacion.BARRAS_ALTURAS.length;

    /** Cuanto se espera sin recibir espectro antes de volver a la animacion decorativa. */
    private static final long ESPERA_SIN_DATOS_MS = 400;

    /** Fraccion que baja la barra en cada cuadro. */
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

    /** Arranca o detiene el ecualizador segun este sonando algo. */
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

    /** Mueve las barras con el espectro real de la musica. */
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

    /** Devuelve las barras a la animacion decorativa si hace rato que no llegan datos. */
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

    /** Detiene todo. */
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
