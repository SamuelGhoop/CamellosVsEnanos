package com.eia.reproductor.controlador;

import javafx.scene.Cursor;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/**
 * Devuelve el redimensionado a una ventana sin decoracion del sistema.
 *
 * <p>Al pasar el escenario a {@code UNDECORATED} desaparece el marco de Windows y con el la
 * capacidad de arrastrar los bordes para cambiar el tamanio. Esta clase la reimplementa: vigila el
 * puntero cerca de los bordes de la ventana, cambia el cursor para indicar que ahi se puede
 * arrastrar, y al hacerlo recalcula posicion y tamanio.</p>
 *
 * <p>Se respetan los tamanios minimos del escenario, de modo que la ventana no se puede encoger
 * hasta romper la maquetacion. Estando maximizada no hace nada, igual que en cualquier
 * aplicacion.</p>
 */
public final class RedimensionadorVentana {

    /** Franja, en pixeles desde el borde, donde el puntero activa el redimensionado. */
    private static final int MARGEN = 7;

    private final Stage ventana;
    private final Region raiz;

    private Cursor zonaActiva = Cursor.DEFAULT;
    private double xInicial;
    private double yInicial;
    private double anchoInicial;
    private double altoInicial;
    private double ventanaXInicial;
    private double ventanaYInicial;

    private RedimensionadorVentana(Stage ventana, Region raiz) {
        this.ventana = ventana;
        this.raiz = raiz;
    }

    /**
     * Instala el redimensionado sobre una ventana.
     *
     * @param ventana escenario sin decoracion
     * @param raiz    nodo raiz de su escena
     */
    public static void instalar(Stage ventana, Region raiz) {
        RedimensionadorVentana redimensionador = new RedimensionadorVentana(ventana, raiz);
        raiz.setOnMouseMoved(redimensionador::alMover);
        raiz.setOnMousePressed(redimensionador::alPresionar);
        raiz.setOnMouseDragged(redimensionador::alArrastrar);
        raiz.setOnMouseExited(evento -> raiz.setCursor(Cursor.DEFAULT));
    }

    private void alMover(javafx.scene.input.MouseEvent evento) {
        if (ventana.isMaximized()) {
            raiz.setCursor(Cursor.DEFAULT);
            return;
        }
        zonaActiva = zonaDe(evento.getX(), evento.getY());
        raiz.setCursor(zonaActiva);
    }

    private void alPresionar(javafx.scene.input.MouseEvent evento) {
        xInicial = evento.getScreenX();
        yInicial = evento.getScreenY();
        anchoInicial = ventana.getWidth();
        altoInicial = ventana.getHeight();
        ventanaXInicial = ventana.getX();
        ventanaYInicial = ventana.getY();
    }

    private void alArrastrar(javafx.scene.input.MouseEvent evento) {
        if (zonaActiva == Cursor.DEFAULT || ventana.isMaximized()) {
            return;
        }
        double avanceX = evento.getScreenX() - xInicial;
        double avanceY = evento.getScreenY() - yInicial;

        if (tocaDerecha(zonaActiva)) {
            ventana.setWidth(Math.max(ventana.getMinWidth(), anchoInicial + avanceX));
        }
        if (tocaAbajo(zonaActiva)) {
            ventana.setHeight(Math.max(ventana.getMinHeight(), altoInicial + avanceY));
        }
        if (tocaIzquierda(zonaActiva)) {
            // Al tirar del borde izquierdo la ventana crece y ademas se desplaza: hay que mover
            // la esquina, no solo cambiar el ancho, o la ventana "huiria" hacia la derecha.
            double ancho = Math.max(ventana.getMinWidth(), anchoInicial - avanceX);
            ventana.setX(ventanaXInicial + (anchoInicial - ancho));
            ventana.setWidth(ancho);
        }
        if (tocaArriba(zonaActiva)) {
            double alto = Math.max(ventana.getMinHeight(), altoInicial - avanceY);
            ventana.setY(ventanaYInicial + (altoInicial - alto));
            ventana.setHeight(alto);
        }
    }

    /** Traduce la posicion del puntero a la esquina o lado que le corresponde. */
    private Cursor zonaDe(double x, double y) {
        boolean izquierda = x < MARGEN;
        boolean derecha = x > raiz.getWidth() - MARGEN;
        boolean arriba = y < MARGEN;
        boolean abajo = y > raiz.getHeight() - MARGEN;

        if (arriba && izquierda) {
            return Cursor.NW_RESIZE;
        }
        if (arriba && derecha) {
            return Cursor.NE_RESIZE;
        }
        if (abajo && izquierda) {
            return Cursor.SW_RESIZE;
        }
        if (abajo && derecha) {
            return Cursor.SE_RESIZE;
        }
        if (izquierda) {
            return Cursor.W_RESIZE;
        }
        if (derecha) {
            return Cursor.E_RESIZE;
        }
        if (arriba) {
            return Cursor.N_RESIZE;
        }
        if (abajo) {
            return Cursor.S_RESIZE;
        }
        return Cursor.DEFAULT;
    }

    private static boolean tocaIzquierda(Cursor zona) {
        return zona == Cursor.W_RESIZE || zona == Cursor.NW_RESIZE || zona == Cursor.SW_RESIZE;
    }

    private static boolean tocaDerecha(Cursor zona) {
        return zona == Cursor.E_RESIZE || zona == Cursor.NE_RESIZE || zona == Cursor.SE_RESIZE;
    }

    private static boolean tocaArriba(Cursor zona) {
        return zona == Cursor.N_RESIZE || zona == Cursor.NW_RESIZE || zona == Cursor.NE_RESIZE;
    }

    private static boolean tocaAbajo(Cursor zona) {
        return zona == Cursor.S_RESIZE || zona == Cursor.SW_RESIZE || zona == Cursor.SE_RESIZE;
    }
}
