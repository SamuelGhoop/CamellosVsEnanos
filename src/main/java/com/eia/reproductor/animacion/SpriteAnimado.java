package com.eia.reproductor.animacion;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

import java.io.InputStream;
import java.util.Objects;

/** Reproduce una hoja de sprites horizontal. */
public class SpriteAnimado {
    private final ImageView vista = new ImageView();
    private final int anchoCuadro;
    private final int altoCuadro;
    private final int cantidadCuadros;
    private final double anchoEnPantalla;
    private final double altoEnPantalla;

    private Timeline ciclo;
    private int cuadroActual;

    /** Carga una hoja y deja el sprite listo en su primer cuadro. */
    public SpriteAnimado(String ruta, int cantidadCuadros, int anchoCuadro, int altoCuadro,
                         int escala, double cuadrosPorSegundo) {
        if (escala < 1) {
            throw new IllegalArgumentException(
                    "La escala debe ser un entero de 1 en adelante, se recibio: " + escala);
        }
        this.cantidadCuadros = cantidadCuadros;
        this.anchoCuadro = anchoCuadro;
        this.altoCuadro = altoCuadro;
        this.anchoEnPantalla = anchoCuadro * (double) escala;
        this.altoEnPantalla = altoCuadro * (double) escala;

        vista.setImage(cargar(ruta));
        vista.setSmooth(false);
        vista.setPreserveRatio(false);
        vista.setFitWidth(anchoEnPantalla);
        vista.setFitHeight(altoEnPantalla);
        mostrarCuadro(0);

        crearCiclo(cuadrosPorSegundo);
    }

    /** @return el nodo que hay que agregar a la escena */
    public ImageView nodo() {
        return vista;
    }

    /** @return ancho del sprite ya escalado */
    public double ancho() {
        return anchoEnPantalla;
    }

    /** @return alto del sprite ya escalado */
    public double alto() {
        return altoEnPantalla;
    }

    /** Arranca la animacion, si no estaba corriendo. */
    public void iniciar() {
        if (ciclo != null && ciclo.getStatus() != Animation.Status.RUNNING) {
            ciclo.play();
        }
    }

    /** Detiene la animacion y deja el sprite en el cuadro en el que iba. */
    public void detener() {
        if (ciclo != null) {
            ciclo.stop();
        }
    }

    private void crearCiclo(double cuadrosPorSegundo) {
        if (cantidadCuadros <= 1 || cuadrosPorSegundo <= 0) {
            return;
        }
        Duration porCuadro = Duration.seconds(1.0 / cuadrosPorSegundo);
        ciclo = new Timeline(new KeyFrame(porCuadro,
                evento -> mostrarCuadro((cuadroActual + 1) % cantidadCuadros)));
        ciclo.setCycleCount(Animation.INDEFINITE);
    }

    private void mostrarCuadro(int indice) {
        cuadroActual = indice;
        vista.setViewport(new Rectangle2D(
                (double) indice * anchoCuadro, 0, anchoCuadro, altoCuadro));
    }

    /** Cambia la hoja de sprites sin interrumpir la animacion. */
    public void cambiarHoja(String ruta) {
        vista.setImage(cargar(ruta));
        // El viewport apunta al cuadro que ya estaba: se conserva la pose exacta.
        mostrarCuadro(cuadroActual);
    }

    private static Image cargar(String ruta) {
        try (InputStream flujo = SpriteAnimado.class.getResourceAsStream(ruta)) {
            return new Image(Objects.requireNonNull(flujo, "No se encontró el sprite " + ruta));
        } catch (Exception excepcion) {
            throw new IllegalStateException("No se pudo cargar el sprite " + ruta, excepcion);
        }
    }
}
