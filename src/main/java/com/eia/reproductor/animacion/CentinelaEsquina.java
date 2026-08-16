package com.eia.reproductor.animacion;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.InputStream;
import java.util.Objects;

/** El personaje que se queda de pie sobre su plataforma, en la esquina inferior derecha. */
public class CentinelaEsquina {
    private final Group grupo = new Group();
    private final SpriteAnimado sprite;
    private final double ancho;
    private final double alto;

    /** Monta el personaje sobre su plataforma. */
    public CentinelaEsquina() {
        sprite = new SpriteAnimado(
                AjustesAnimacion.RUTA_CENTINELA,
                AjustesAnimacion.CENTINELA_CUADROS,
                AjustesAnimacion.CENTINELA_ANCHO_CUADRO,
                AjustesAnimacion.CENTINELA_ALTO_CUADRO,
                AjustesAnimacion.ESCALA,
                AjustesAnimacion.CENTINELA_FPS);

        double escala = AjustesAnimacion.ESCALA;
        double filaPies = AjustesAnimacion.CENTINELA_FILA_PIES * escala;

        if (!AjustesAnimacion.CENTINELA_CON_PLATAFORMA) {
            // Sin plataforma: el alto util llega hasta los pies, no hasta el borde del cuadro.
            // Asi, al apoyarlo contra el borde inferior, no quedan flotando esos 4 px de aire.
            ancho = sprite.ancho();
            alto = filaPies;
            sprite.nodo().setLayoutX(0);
            sprite.nodo().setLayoutY(0);
            grupo.getChildren().add(sprite.nodo());
            return;
        }

        double anchoMarco = AjustesAnimacion.MARCO_ANCHO * escala;
        double altoMarco = AjustesAnimacion.MARCO_ALTO * escala;

        ImageView marco = new ImageView(cargar(AjustesAnimacion.RUTA_MARCO));
        marco.setSmooth(false);
        marco.setPreserveRatio(false);
        marco.setFitWidth(anchoMarco);
        marco.setFitHeight(altoMarco);

        ancho = Math.max(sprite.ancho(), anchoMarco);
        // El alto util llega hasta los pies: los 4 px de aire que el cuadro tiene debajo no
        // cuentan, o el personaje quedaria flotando al apoyarlo contra el borde de la ventana.
        alto = filaPies;

        // El marco se alinea por abajo con la linea de los pies, de modo que el personaje queda
        // parado dentro del octogono y no encima de el.
        marco.setLayoutX((ancho - anchoMarco) / 2);
        marco.setLayoutY(filaPies - altoMarco);
        sprite.nodo().setLayoutX((ancho - sprite.ancho()) / 2);
        sprite.nodo().setLayoutY(0);

        // El marco va primero para que quede detras del personaje.
        grupo.getChildren().addAll(marco, sprite.nodo());
    }

    /** @return el nodo a colocar en la capa de animaciones */
    public Node nodo() {
        return grupo;
    }

    /** @return ancho total del conjunto personaje + plataforma */
    public double ancho() {
        return ancho;
    }

    /** @return alto total del conjunto personaje + plataforma */
    public double alto() {
        return alto;
    }

    /** Recoloca el conjunto en la esquina inferior izquierda del area disponible. */
    public void recolocar(double anchoDisponible, double altoDisponible) {
        grupo.setLayoutX(AjustesAnimacion.CENTINELA_MARGEN_IZQUIERDO);
        grupo.setLayoutY(altoDisponible - alto - AjustesAnimacion.CENTINELA_MARGEN_INFERIOR);
    }

    /** Arranca la animacion. */
    public void iniciar() {
        sprite.iniciar();
    }

    /** Detiene la animacion. */
    public void detener() {
        sprite.detener();
    }

    private static Image cargar(String ruta) {
        try (InputStream flujo = CentinelaEsquina.class.getResourceAsStream(ruta)) {
            return new Image(Objects.requireNonNull(flujo, "No se encontró la imagen " + ruta));
        } catch (Exception excepcion) {
            throw new IllegalStateException("No se pudo cargar la imagen " + ruta, excepcion);
        }
    }
    /** Cambia entre el traje rojo y el negro. */
    public void usarTrajeNegro(boolean oscuro) {
        sprite.cambiarHoja(oscuro
                ? AjustesAnimacion.RUTA_CENTINELA_NEGRO
                : AjustesAnimacion.RUTA_CENTINELA);
    }

}
