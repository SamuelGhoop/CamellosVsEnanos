package com.eia.reproductor.animacion;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.Random;

/** El personaje que baja colgado de una telarania cada tanto y despues vuelve a subir. */
public class VisitanteColgante {
    private final Group grupo = new Group();
    private final Line telarana = new Line();
    private final SpriteAnimado sprite;
    private final Random azar = new Random();

    /** Cuanto ha descendido la punta de la telarania desde el borde superior. */
    private final DoubleProperty profundidad = new SimpleDoubleProperty();

    /**
     * Altura del borde desde el que cuelga: el techo del area de contenido, no el de la ventana.
     */
    private final DoubleProperty origenY = new SimpleDoubleProperty();

    /** Recorta lo que quede por encima del origen, para que no se vea sobre la cabecera. */
    private final Rectangle recorte = new Rectangle();

    private SequentialTransition visita;
    private double anchoDisponible;
    private double altoDisponible;
    private boolean activo;

    /** Monta el colgante, oculto por encima del borde superior. */
    public VisitanteColgante() {
        sprite = new SpriteAnimado(
                AjustesAnimacion.RUTA_COLGANTE,
                AjustesAnimacion.COLGANTE_CUADROS,
                AjustesAnimacion.COLGANTE_ANCHO_CUADRO,
                AjustesAnimacion.COLGANTE_ALTO_CUADRO,
                AjustesAnimacion.ESCALA,
                AjustesAnimacion.COLGANTE_FPS);

        telarana.setStroke(Color.web(AjustesAnimacion.TELARANA_COLOR));
        telarana.setStrokeWidth(AjustesAnimacion.TELARANA_GROSOR);

        // El sprite sigue a la punta de la telarania. Se le resta 1 px de solape para que no se
        // vea una costura entre la linea dibujada y el trozo de red que trae el propio sprite.
        telarana.startYProperty().bind(origenY);
        telarana.endYProperty().bind(origenY.add(profundidad));
        sprite.nodo().layoutYProperty().bind(origenY.add(profundidad).subtract(1));

        recorte.setX(0);
        recorte.yProperty().bind(origenY);
        grupo.setClip(recorte);

        grupo.getChildren().addAll(telarana, sprite.nodo());
        grupo.setVisible(false);
        profundidad.set(-sprite.alto());
    }

    /** @return el nodo a colocar en la capa de animaciones */
    public Node nodo() {
        return grupo;
    }

    /** @return altura del borde superior desde el que cuelga; se ata a la altura de la cabecera */
    public DoubleProperty origenYProperty() {
        return origenY;
    }

    /** Informa el tamanio del area por la que puede moverse. */
    public void redimensionar(double ancho, double alto) {
        this.anchoDisponible = ancho;
        this.altoDisponible = alto;
        recorte.setWidth(ancho);
        recorte.setHeight(Math.max(0, alto - origenY.get()));
    }

    /** Empieza a programar visitas. */
    public void iniciar() {
        if (activo) {
            return;
        }
        activo = true;
        programarProximaVisita(AjustesAnimacion.COLGANTE_ESPERA_INICIAL);
    }

    /** Corta las visitas y esconde al personaje. */
    public void detener() {
        activo = false;
        if (visita != null) {
            visita.stop();
        }
        sprite.detener();
        grupo.setVisible(false);
    }

    // --- Apoyo interno ---

    private void programarProximaVisita(Duration espera) {
        PauseTransition pausa = new PauseTransition(espera);
        pausa.setOnFinished(evento -> bajar());
        pausa.play();
    }

    private void bajar() {
        if (!activo || anchoDisponible <= 0 || altoDisponible <= 0) {
            // Todavia no hay layout: se reintenta mas tarde en vez de aparecer en una esquina.
            programarProximaVisita(AjustesAnimacion.COLGANTE_ESPERA_MINIMA);
            return;
        }

        colocarEnColumnaAlAzar();
        double destino = sortearProfundidad();

        profundidad.set(-sprite.alto());
        grupo.setVisible(true);
        sprite.iniciar();

        Timeline descenso = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(profundidad, -sprite.alto())),
                new KeyFrame(AjustesAnimacion.COLGANTE_BAJADA,
                        new KeyValue(profundidad, destino, Interpolator.EASE_OUT)));

        PauseTransition quedarse = new PauseTransition(AjustesAnimacion.COLGANTE_PERMANENCIA);

        Timeline ascenso = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(profundidad, destino)),
                new KeyFrame(AjustesAnimacion.COLGANTE_SUBIDA,
                        new KeyValue(profundidad, -sprite.alto(), Interpolator.EASE_IN)));

        visita = new SequentialTransition(descenso, quedarse, ascenso);
        visita.setOnFinished(evento -> {
            grupo.setVisible(false);
            sprite.detener();
            if (activo) {
                programarProximaVisita(sortearEspera());
            }
        });
        visita.play();
    }

    /** Elige una columna dentro de la franja permitida y alinea la telarania con el sprite. */
    private void colocarEnColumnaAlAzar() {
        double desde = anchoDisponible * AjustesAnimacion.COLGANTE_FRANJA_IZQUIERDA;
        double hasta = anchoDisponible * AjustesAnimacion.COLGANTE_FRANJA_DERECHA;
        double columna = desde + azar.nextDouble() * Math.max(1, hasta - desde);

        telarana.setStartX(columna);
        telarana.setEndX(columna);
        // startY no se toca: esta atado a origenY. Asignarlo a mano lanza
        // "A bound value cannot be set" y se lleva por delante el hilo de la interfaz.
        sprite.nodo().setLayoutX(columna - AjustesAnimacion.COLGANTE_COLUMNA_TELARANA
                * AjustesAnimacion.ESCALA);
    }

    /**
     * Sortea cuanto baja, recortando el resultado para que el sprite entero quede por encima de la
     * franja intocable.
     */
    private double sortearProfundidad() {
        double tope = altoDisponible
                - AjustesAnimacion.COLGANTE_ZONA_PROHIBIDA_ABAJO
                - sprite.alto();
        double maximo = Math.min(AjustesAnimacion.COLGANTE_DESCENSO_MAXIMO, tope);
        double minimo = Math.min(AjustesAnimacion.COLGANTE_DESCENSO_MINIMO, Math.max(0, maximo));
        if (maximo <= minimo) {
            return Math.max(0, minimo);
        }
        return minimo + azar.nextDouble() * (maximo - minimo);
    }

    private Duration sortearEspera() {
        double minimo = AjustesAnimacion.COLGANTE_ESPERA_MINIMA.toSeconds();
        double maximo = AjustesAnimacion.COLGANTE_ESPERA_MAXIMA.toSeconds();
        return Duration.seconds(minimo + azar.nextDouble() * Math.max(0, maximo - minimo));
    }

    /** @return {@code true} si hay una visita en curso; util para las pruebas manuales */
    public boolean estaVisible() {
        return grupo.isVisible() && visita != null
                && visita.getStatus() == Animation.Status.RUNNING;
    }
    /** Cambia entre el traje rojo y el negro. */
    public void usarTrajeNegro(boolean oscuro) {
        sprite.cambiarHoja(oscuro
                ? AjustesAnimacion.RUTA_COLGANTE_NEGRO
                : AjustesAnimacion.RUTA_COLGANTE);
    }

}
