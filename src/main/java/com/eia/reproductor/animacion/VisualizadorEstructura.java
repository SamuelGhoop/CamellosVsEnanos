package com.eia.reproductor.animacion;

import com.eia.reproductor.modelo.EstructuraVisual;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Dibuja la estructura de datos que el modo activo tiene cargada en este momento.
 *
 * <p>No es una ilustracion fija: recibe la forma real y la pinta. Si el arbol degenera porque las
 * canciones entraron ya ordenadas, se ve degenerar; si la cola se vacia, se ve vaciarse.</p>
 *
 * <p>El {@code switch} sobre {@link EstructuraVisual} no lleva {@code default} a proposito: como
 * el tipo es sellado, el compilador comprueba que estan los tres casos. Si algun dia se agrega un
 * cuarto modo, el proyecto no compilara hasta decidir como se dibuja, en vez de mostrar un panel
 * en blanco.</p>
 */
public class VisualizadorEstructura {

    private static final double ANCHO = 720;
    private static final double ALTO = 380;

    /** Lado de la caja de cada elemento. Cuadrada, como el resto de la estetica. */
    private static final double LADO_CAJA = 74;
    private static final double ALTO_CAJA = 30;

    private static final Color FONDO = Color.web("#0D0D14");
    private static final Color CAJA = Color.web("#161620");
    private static final Color BORDE = Color.web("#296287");
    private static final Color TEXTO = Color.web("#E6F5F8");
    private static final Color RESALTE = Color.web("#4FC3E8");
    private static final Color APAGADO = Color.web("#5A6472");

    private final Canvas lienzo = new Canvas(ANCHO, ALTO);
    private final Label titulo = new Label();
    private final Label leyenda = new Label();
    private final VBox nodo = new VBox(8);

    /** Construye el panel vacio. */
    public VisualizadorEstructura() {
        titulo.getStyleClass().add("panel-encabezado");
        leyenda.getStyleClass().add("texto-tenue");
        leyenda.setWrapText(true);
        leyenda.setTextAlignment(TextAlignment.CENTER);

        nodo.setAlignment(Pos.CENTER);
        nodo.getStyleClass().add("cuerpo-dialogo");
        nodo.getChildren().addAll(titulo, lienzo, leyenda);
    }

    /** @return el nodo para colgarlo de una escena */
    public VBox nodo() {
        return nodo;
    }

    /**
     * Repinta el panel con el estado actual de la estructura.
     *
     * @param estructura descripcion que entrega el modo activo
     */
    public void mostrar(EstructuraVisual estructura) {
        GraphicsContext pincel = lienzo.getGraphicsContext2D();
        pincel.setFill(FONDO);
        pincel.fillRect(0, 0, ANCHO, ALTO);
        pincel.setFont(Font.font("Press Start 2P", 8));

        if (estructura == null) {
            return;
        }
        titulo.setText("• " + estructura.nombre().toUpperCase());

        switch (estructura) {
            case EstructuraVisual.Anillo anillo -> dibujarAnillo(pincel, anillo);
            case EstructuraVisual.Cola cola -> dibujarCola(pincel, cola);
            case EstructuraVisual.Arbol arbol -> dibujarArbol(pincel, arbol);
        }
    }

    // ------------------------------------------------------------------
    // Lista circular doble
    // ------------------------------------------------------------------

    /**
     * Dibuja el anillo en circulo, con flechas en los dos sentidos.
     *
     * <p>Se pinta en circulo y no en fila justamente para que se vea que no hay principio ni
     * final: es lo que distingue a esta estructura de una lista normal.</p>
     */
    private void dibujarAnillo(GraphicsContext pincel, EstructuraVisual.Anillo anillo) {
        int cuantos = anillo.etiquetas().size();
        if (cuantos == 0) {
            mensajeVacio(pincel, "La lista está vacía");
            return;
        }
        leyenda.setText("Los enlaces van en los dos sentidos y el último vuelve al primero: "
                + "por eso nunca hay final de reproducción. El cursor marca dónde vas.");

        double centroX = ANCHO / 2;
        double centroY = ALTO / 2 - 10;
        double radio = Math.min(centroX, centroY) - LADO_CAJA / 2 - 12;
        int visibles = Math.min(cuantos, 12);

        // Anillo de fondo: la circularidad se ve antes de leer una sola etiqueta.
        pincel.setStroke(APAGADO);
        pincel.setLineWidth(2);
        pincel.strokeOval(centroX - radio, centroY - radio, radio * 2, radio * 2);

        for (int i = 0; i < visibles; i++) {
            double angulo = 2 * Math.PI * i / visibles - Math.PI / 2;
            double x = centroX + radio * Math.cos(angulo) - LADO_CAJA / 2;
            double y = centroY + radio * Math.sin(angulo) - ALTO_CAJA / 2;
            caja(pincel, x, y, LADO_CAJA, anillo.etiquetas().get(i), i == anillo.indiceActual());
        }

        if (cuantos > visibles) {
            pincel.setFill(APAGADO);
            pincel.fillText("y " + (cuantos - visibles) + " más en el anillo", 12, ALTO - 10);
        }
        if (anillo.indiceActual() >= 0) {
            pincel.setFill(RESALTE);
            pincel.fillText("CURSOR", centroX - 24, centroY);
        }
    }

    // ------------------------------------------------------------------
    // Cola FIFO
    // ------------------------------------------------------------------

    private void dibujarCola(GraphicsContext pincel, EstructuraVisual.Cola cola) {
        leyenda.setText("Entra por la derecha y sale por la izquierda. Las que ya sonaron "
                + "salieron de la cola de verdad: no se quedan con un índice apuntándolas.");

        pincel.setFill(TEXTO);
        pincel.fillText("SALE  <—", 12, ALTO / 2 - 28);
        pincel.fillText("<—  ENTRA", ANCHO - 90, ALTO / 2 - 28);

        int cuantos = cola.etiquetas().size();
        if (cuantos == 0) {
            mensajeVacio(pincel, "La cola se vació: ya sonaron todas");
        } else {
            int visibles = Math.min(cuantos, 7);
            double ancho = LADO_CAJA + 14;
            double inicioX = (ANCHO - visibles * ancho) / 2;
            for (int i = 0; i < visibles; i++) {
                caja(pincel, inicioX + i * ancho, ALTO / 2 - ALTO_CAJA / 2, LADO_CAJA,
                        cola.etiquetas().get(i), i == 0);
            }
            pincel.setFill(RESALTE);
            pincel.fillText("FRENTE", inicioX, ALTO / 2 + 34);
            if (cuantos > visibles) {
                pincel.setFill(APAGADO);
                pincel.fillText("+" + (cuantos - visibles), inicioX + visibles * ancho + 6,
                        ALTO / 2 + 4);
            }
        }
        pincel.setFill(APAGADO);
        pincel.fillText("ya salieron de la cola: " + cola.yaSalieron(), 12, ALTO - 10);
    }

    // ------------------------------------------------------------------
    // Arbol binario de busqueda
    // ------------------------------------------------------------------

    private void dibujarArbol(GraphicsContext pincel, EstructuraVisual.Arbol arbol) {
        if (arbol.raiz() == null) {
            mensajeVacio(pincel, "El árbol está vacío");
            return;
        }
        int altura = arbol.raiz().altura();
        leyenda.setText("Izquierda = antes alfabéticamente, derecha = después. Altura " + altura
                + ". Si las canciones entran ya ordenadas se convierte en una sola rama y todo "
                + "pasa a ser O(n).");

        double altoNivel = Math.min(70, (ALTO - 40) / Math.max(1, altura));
        dibujarRama(pincel, arbol.raiz(), ANCHO / 2, 24, ANCHO / 4, altoNivel, arbol.actual());
    }

    /** Reparte el ancho a la mitad en cada nivel, que es como se dibuja un arbol binario. */
    private void dibujarRama(GraphicsContext pincel, EstructuraVisual.Rama rama,
                             double x, double y, double separacion, double altoNivel,
                             String actual) {
        if (rama == null) {
            return;
        }
        pincel.setStroke(BORDE);
        pincel.setLineWidth(2);

        if (rama.izquierdo() != null) {
            pincel.strokeLine(x, y + ALTO_CAJA / 2, x - separacion, y + altoNivel);
            dibujarRama(pincel, rama.izquierdo(), x - separacion, y + altoNivel,
                    separacion / 2, altoNivel, actual);
        }
        if (rama.derecho() != null) {
            pincel.strokeLine(x, y + ALTO_CAJA / 2, x + separacion, y + altoNivel);
            dibujarRama(pincel, rama.derecho(), x + separacion, y + altoNivel,
                    separacion / 2, altoNivel, actual);
        }
        // El nodo se pinta despues de sus lineas para que quede por encima de ellas.
        caja(pincel, x - LADO_CAJA / 2, y, LADO_CAJA, rama.etiqueta(),
                rama.etiqueta().equals(actual));
    }

    // ------------------------------------------------------------------
    // Piezas comunes
    // ------------------------------------------------------------------

    /** Una caja con su etiqueta recortada, resaltada si es la cancion en curso. */
    private void caja(GraphicsContext pincel, double x, double y, double ancho,
                      String etiqueta, boolean resaltada) {
        pincel.setFill(CAJA);
        pincel.fillRect(x, y, ancho, ALTO_CAJA);
        pincel.setStroke(resaltada ? RESALTE : BORDE);
        pincel.setLineWidth(resaltada ? 3 : 2);
        pincel.strokeRect(x, y, ancho, ALTO_CAJA);

        pincel.setFill(resaltada ? RESALTE : TEXTO);
        pincel.fillText(recortar(etiqueta), x + 5, y + ALTO_CAJA / 2 + 4);
    }

    private void mensajeVacio(GraphicsContext pincel, String texto) {
        leyenda.setText("");
        pincel.setFill(APAGADO);
        pincel.fillText(texto, ANCHO / 2 - texto.length() * 3, ALTO / 2);
    }

    /** A 8 px por caracter, en la caja caben unas nueve letras. */
    private static String recortar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= 9 ? texto : texto.substring(0, 8) + "…";
    }
}
