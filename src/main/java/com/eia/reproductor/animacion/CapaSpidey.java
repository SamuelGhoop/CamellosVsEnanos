package com.eia.reproductor.animacion;

import javafx.scene.layout.Pane;

/** Capa transparente que se monta encima de la interfaz y aloja las animaciones decorativas. */
public class CapaSpidey {
    private final Pane capa = new Pane();
    private final CentinelaEsquina centinela = new CentinelaEsquina();
    private final VisitanteColgante colgante = new VisitanteColgante();

    /** Construye la capa con sus dos animaciones, todavia detenidas. */
    public CapaSpidey() {
        capa.setMouseTransparent(true);
        capa.setPickOnBounds(false);
        capa.getChildren().addAll(colgante.nodo(), centinela.nodo());

        // Las animaciones se recolocan solas cuando la ventana cambia de tamanio.
        capa.widthProperty().addListener((observable, anterior, actual) -> reacomodar());
        capa.heightProperty().addListener((observable, anterior, actual) -> reacomodar());
    }

    /** @return la capa para superponerla sobre la interfaz */
    public Pane nodo() {
        return capa;
    }

    /** Altura del borde desde el que baja el colgante. */
    public javafx.beans.property.DoubleProperty origenTelaranaProperty() {
        return colgante.origenYProperty();
    }

    /** Arranca las animaciones. */
    public void iniciar() {
        reacomodar();
        centinela.iniciar();
        colgante.iniciar();
    }

    /** Detiene todas las animaciones. */
    public void detener() {
        centinela.detener();
        colgante.detener();
    }

    private void reacomodar() {
        double ancho = capa.getWidth();
        double alto = capa.getHeight();
        if (ancho <= 0 || alto <= 0) {
            return;
        }
        centinela.recolocar(ancho, alto);
        colgante.redimensionar(ancho, alto);
    }
    /** Cambia el traje de los dos personajes segun el tema. */
    public void usarTrajeNegro(boolean oscuro) {
        centinela.usarTrajeNegro(oscuro);
        colgante.usarTrajeNegro(oscuro);
    }

}
