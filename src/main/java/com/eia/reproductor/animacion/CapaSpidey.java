package com.eia.reproductor.animacion;

import javafx.scene.layout.Pane;

/**
 * Capa transparente que se monta encima de la interfaz y aloja las animaciones decorativas.
 *
 * <p>Es el unico punto de contacto entre el reproductor y todo este paquete: el controlador crea
 * una capa, la superpone y llama {@link #iniciar()}. No hay ninguna otra dependencia, y en
 * particular <b>nada de aqui toca el modelo, las estructuras de datos ni los servicios</b>: si se
 * borrara el paquete entero, la aplicacion seguiria funcionando igual, solo que sin adornos.</p>
 *
 * <p><b>No intercepta clics.</b> La capa esta marcada como transparente al raton, asi que los
 * eventos la atraviesan y llegan a la tabla y a los botones que hay debajo. Sin esto, un sprite
 * paseandose por encima de la interfaz bloquearia la aplicacion cada vez que pasara sobre un
 * boton.</p>
 */
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

    /**
     * Altura del borde desde el que baja el colgante.
     *
     * <p>El controlador la ata a la altura de la cabecera para que la telarania nazca en el techo
     * del area de contenido y no encima del titulo.</p>
     *
     * @return la propiedad a enlazar
     */
    public javafx.beans.property.DoubleProperty origenTelaranaProperty() {
        return colgante.origenYProperty();
    }

    /** Arranca las animaciones. */
    public void iniciar() {
        reacomodar();
        centinela.iniciar();
        colgante.iniciar();
    }

    /** Detiene todas las animaciones. Se llama al cerrar la aplicacion. */
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
    /**
     * Cambia el traje de los dos personajes segun el tema.
     *
     * <p>Lo llama el controlador al alternar entre claro y oscuro. Las hojas negras tienen la
     * misma rejilla que las rojas, asi que el cambio es instantaneo y sin cortar la animacion.</p>
     *
     * @param oscuro {@code true} para el traje negro
     */
    public void usarTrajeNegro(boolean oscuro) {
        centinela.usarTrajeNegro(oscuro);
        colgante.usarTrajeNegro(oscuro);
    }

}
