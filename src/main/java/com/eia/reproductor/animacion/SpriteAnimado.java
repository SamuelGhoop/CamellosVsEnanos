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

/**
 * Reproduce una hoja de sprites horizontal.
 *
 * <p>La hoja se carga una sola vez y lo que va cambiando es la <i>ventana de recorte</i>
 * ({@link ImageView#setViewport(Rectangle2D)}) que decide que trozo se ve. Es mucho mas barato que
 * tener una imagen por cuadro: la textura queda cargada en memoria de video y en cada paso solo se
 * mueve un rectangulo.</p>
 *
 * <p>El dibujo se muestra sin suavizado ({@code setSmooth(false)}, que es el equivalente en JavaFX
 * del vecino mas cercano de Java2D) y a escala entera, que son las dos condiciones para que el
 * pixel art no se vea borroso ni con los pixeles de tamanios distintos.</p>
 */
public class SpriteAnimado {

    private final ImageView vista = new ImageView();
    private final int anchoCuadro;
    private final int altoCuadro;
    private final int cantidadCuadros;
    private final double anchoEnPantalla;
    private final double altoEnPantalla;

    private Timeline ciclo;
    private int cuadroActual;

    /**
     * Carga una hoja y deja el sprite listo en su primer cuadro.
     *
     * @param ruta            recurso de la hoja dentro del classpath
     * @param cantidadCuadros cuantos cuadros contiene, medidos sobre la hoja
     * @param anchoCuadro     ancho de cada cuadro en pixeles de la hoja
     * @param altoCuadro      alto de cada cuadro en pixeles de la hoja
     * @param escala          ampliacion entera
     * @param cuadrosPorSegundo velocidad de la animacion
     * @throws IllegalArgumentException si la escala no es un entero positivo
     * @throws IllegalStateException    si la hoja no esta en el classpath
     */
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

    private static Image cargar(String ruta) {
        try (InputStream flujo = SpriteAnimado.class.getResourceAsStream(ruta)) {
            return new Image(Objects.requireNonNull(flujo, "No se encontró el sprite " + ruta));
        } catch (Exception excepcion) {
            throw new IllegalStateException("No se pudo cargar el sprite " + ruta, excepcion);
        }
    }
}
