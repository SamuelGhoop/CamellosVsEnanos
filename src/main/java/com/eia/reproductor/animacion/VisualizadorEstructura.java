package com.eia.reproductor.animacion;

import com.eia.reproductor.modelo.EstructuraVisual;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;

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

    /** Lo que se ve sin desplazarse. El lienzo puede ser mayor y entonces aparece la barra. */
    private static final double ANCHO_VISIBLE = 760;
    private static final double ALTO_VISIBLE = 400;

    private static final double ANCHO_CAJA = 116;
    private static final double ALTO_CAJA = 30;

    /**
     * Distancia entre columnas del arbol.
     *
     * <p>Es mayor que la caja a proposito: al repartir una columna por nodo, esta separacion es la
     * que garantiza que dos cajas no puedan tocarse nunca.</p>
     */
    private static final double PASO_COLUMNA = ANCHO_CAJA + 10;
    private static final double PASO_NIVEL = 52;
    private static final double MARGEN = 20;

    private static final Color FONDO = Color.web("#0D0D14");
    private static final Color CAJA = Color.web("#161620");
    private static final Color BORDE = Color.web("#296287");
    private static final Color TEXTO = Color.web("#E6F5F8");
    private static final Color RESALTE = Color.web("#4FC3E8");
    private static final Color APAGADO = Color.web("#5A6472");

    private static final Font LETRA = Font.font("Press Start 2P", 8);

    private final Canvas lienzo = new Canvas(ANCHO_VISIBLE, ALTO_VISIBLE);
    private final ScrollPane marco = new ScrollPane(lienzo);
    private final Label titulo = new Label();
    private final Label leyenda = new Label();
    private final VBox nodo = new VBox(8);

    /** Regla para medir texto: recortar por numero de caracteres falla con titulos anchos. */
    private final Text regla = new Text();

    /** Construye el panel vacio. */
    public VisualizadorEstructura() {
        regla.setFont(LETRA);

        titulo.getStyleClass().add("panel-encabezado");
        leyenda.getStyleClass().add("texto-tenue");
        leyenda.setWrapText(true);
        leyenda.setTextAlignment(TextAlignment.CENTER);
        leyenda.setMaxWidth(ANCHO_VISIBLE);

        // Un arbol de veinte canciones no cabe en ninguna pantalla: en vez de encogerlo hasta que
        // no se lea, se deja a tamanio legible y se desplaza.
        marco.setPrefViewportWidth(ANCHO_VISIBLE);
        marco.setPrefViewportHeight(ALTO_VISIBLE);
        marco.setPannable(true);
        marco.setStyle("-fx-background: #0D0D14; -fx-background-color: #0D0D14;");

        nodo.setAlignment(Pos.CENTER);
        nodo.getStyleClass().add("cuerpo-dialogo");
        nodo.getChildren().addAll(titulo, marco, leyenda);
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
        if (estructura == null) {
            return;
        }
        titulo.setText("• " + estructura.nombre().toUpperCase());

        // Cada estructura dimensiona el lienzo antes de pintar: lo que necesita el anillo no tiene
        // nada que ver con lo que necesita un arbol de veinte canciones.
        switch (estructura) {
            case EstructuraVisual.Anillo anillo -> dibujarAnillo(anillo);
            case EstructuraVisual.Cola cola -> dibujarCola(cola);
            case EstructuraVisual.Arbol arbol -> dibujarArbol(arbol);
        }
    }

    /** Ajusta el lienzo, sin bajar de lo que se ve para que no queden franjas sin pintar. */
    private void redimensionar(double ancho, double alto) {
        lienzo.setWidth(Math.max(ancho, ANCHO_VISIBLE));
        lienzo.setHeight(Math.max(alto, ALTO_VISIBLE));
    }

    /** Deja el lienzo en negro y devuelve el pincel listo para dibujar. */
    private GraphicsContext limpiar() {
        GraphicsContext pincel = lienzo.getGraphicsContext2D();
        pincel.setFill(FONDO);
        pincel.fillRect(0, 0, lienzo.getWidth(), lienzo.getHeight());
        pincel.setFont(LETRA);
        return pincel;
    }

    // ------------------------------------------------------------------
    // Lista circular doble
    // ------------------------------------------------------------------

    /**
     * Dibuja el anillo en circulo, con el cursor en el centro.
     *
     * <p>Se pinta en circulo y no en fila justamente para que se vea que no hay principio ni
     * final: es lo que distingue a esta estructura de una lista normal.</p>
     */
    private void dibujarAnillo(EstructuraVisual.Anillo anillo) {
        int cuantos = anillo.etiquetas().size();
        if (cuantos == 0) {
            redimensionar(ANCHO_VISIBLE, ALTO_VISIBLE);
            mensajeVacio(limpiar(), "La lista está vacía");
            return;
        }
        leyenda.setText("Los enlaces van en los dos sentidos y el último vuelve al primero: "
                + "por eso nunca hay final de reproducción. El cursor marca dónde vas.");

        int visibles = Math.min(cuantos, 12);
        // El radio sale del perimetro que hace falta para que las cajas no se toquen, no de un
        // numero fijo: si no, al ensanchar las cajas se solapan en la parte alta del circulo.
        double radio = Math.max(140, visibles * PASO_COLUMNA / (2 * Math.PI));
        redimensionar(2 * (radio + ANCHO_CAJA / 2 + MARGEN),
                2 * (radio + ALTO_CAJA + MARGEN));

        GraphicsContext pincel = limpiar();
        double centroX = lienzo.getWidth() / 2;
        double centroY = lienzo.getHeight() / 2;

        // Anillo de fondo: la circularidad se ve antes de leer una sola etiqueta.
        pincel.setStroke(APAGADO);
        pincel.setLineWidth(2);
        pincel.strokeOval(centroX - radio, centroY - radio, radio * 2, radio * 2);

        // La aguja del cursor se pinta antes que las cajas para que quede por debajo de ellas.
        if (anillo.indiceActual() >= 0 && anillo.indiceActual() < visibles) {
            double angulo = 2 * Math.PI * anillo.indiceActual() / visibles - Math.PI / 2;
            pincel.setStroke(RESALTE);
            pincel.setLineWidth(2);
            pincel.strokeLine(centroX, centroY,
                    centroX + radio * Math.cos(angulo), centroY + radio * Math.sin(angulo));
        }

        for (int i = 0; i < visibles; i++) {
            double angulo = 2 * Math.PI * i / visibles - Math.PI / 2;
            double x = centroX + radio * Math.cos(angulo);
            double y = centroY + radio * Math.sin(angulo) - ALTO_CAJA / 2;
            caja(pincel, x, y, anillo.etiquetas().get(i), i == anillo.indiceActual());
        }

        if (cuantos > visibles) {
            pincel.setFill(APAGADO);
            pincel.fillText("y " + (cuantos - visibles) + " más en el anillo",
                    12, lienzo.getHeight() - 10);
        }
        if (anillo.indiceActual() >= 0) {
            // Sobre el centro, con la aguja saliendo hacia la cancion que suena: sin la linea,
            // la palabra quedaba flotando sin senialar nada.
            pincel.setFill(RESALTE);
            pincel.fillText("CURSOR", centroX - 24, centroY - 8);
        }
    }

    // ------------------------------------------------------------------
    // Cola FIFO
    // ------------------------------------------------------------------

    private void dibujarCola(EstructuraVisual.Cola cola) {
        leyenda.setText("Entra por la derecha y sale por la izquierda. Las que ya sonaron "
                + "salieron de la cola de verdad: no se quedan con un índice apuntándolas.");

        redimensionar(ANCHO_VISIBLE, ALTO_VISIBLE);
        GraphicsContext pincel = limpiar();
        double medio = lienzo.getHeight() / 2;
        pincel.setFill(TEXTO);
        pincel.fillText("SALE  <—", 12, medio - 28);
        pincel.fillText("<—  ENTRA", lienzo.getWidth() - 90, medio - 28);

        int cuantos = cola.etiquetas().size();
        if (cuantos == 0) {
            mensajeVacio(pincel, "La cola se vació: ya sonaron todas");
        } else {
            int visibles = Math.min(cuantos, 5);
            double inicioX = (lienzo.getWidth() - visibles * PASO_COLUMNA) / 2 + ANCHO_CAJA / 2;
            for (int i = 0; i < visibles; i++) {
                caja(pincel, inicioX + i * PASO_COLUMNA, medio - ALTO_CAJA / 2,
                        cola.etiquetas().get(i), i == 0);
            }
            pincel.setFill(RESALTE);
            pincel.fillText("FRENTE", inicioX - ANCHO_CAJA / 2, medio + 34);
            if (cuantos > visibles) {
                // Debajo de la ultima caja y no a su derecha: ahi se salia del lienzo y el texto
                // aparecia cortado a media palabra.
                pincel.setFill(APAGADO);
                pincel.fillText("+" + (cuantos - visibles) + " esperando",
                        inicioX + (visibles - 1) * PASO_COLUMNA - ANCHO_CAJA / 2, medio + 34);
            }
        }
        pincel.setFill(APAGADO);
        pincel.fillText("ya salieron de la cola: " + cola.yaSalieron(),
                12, lienzo.getHeight() - 10);
    }

    // ------------------------------------------------------------------
    // Arbol binario de busqueda
    // ------------------------------------------------------------------

    /** Un nodo ya colocado, para poder pintar todas las lineas antes que todas las cajas. */
    private record Colocado(String etiqueta, double x, double y, boolean resaltado) { }

    /**
     * Dibuja el arbol repartiendo <b>una columna por nodo</b>, en orden inorden.
     *
     * <p><b>Por que asi y no partiendo el ancho a la mitad en cada nivel.</b> Con el reparto por
     * mitades, la separacion se divide entre dos en cada nivel: 180, 90, 45, 22, 11... A partir del
     * cuarto nivel es menor que el ancho de una caja y los nodos se pisan unos con otros, que es
     * justo lo que pasa con un arbol degenerado —el caso que mas interesa ensenar—. Dando a cada
     * nodo su propia columna, dos cajas no pueden solaparse nunca, y de paso el recorrido inorden
     * queda leible de izquierda a derecha: el orden en que suenan las canciones.</p>
     */
    private void dibujarArbol(EstructuraVisual.Arbol arbol) {
        if (arbol.raiz() == null) {
            redimensionar(ANCHO_VISIBLE, ALTO_VISIBLE);
            mensajeVacio(limpiar(), "El árbol está vacío");
            return;
        }
        int cuantos = contar(arbol.raiz());
        int altura = arbol.raiz().altura();
        leyenda.setText("Izquierda = antes alfabéticamente, derecha = después. " + cuantos
                + " canciones, altura " + altura + ". Leído de izquierda a derecha sale el orden "
                + "alfabético. Si entran ya ordenadas se convierte en una sola rama y todo pasa a "
                + "ser O(n).");

        List<Colocado> nodos = new ArrayList<>();
        List<double[]> lineas = new ArrayList<>();
        double[] siguienteLibre = new double[altura];
        for (int nivel = 0; nivel < altura; nivel++) {
            siguienteLibre[nivel] = MARGEN + ANCHO_CAJA / 2;
        }
        colocar(arbol.raiz(), 0, siguienteLibre, nodos, lineas, arbol.actual());

        double masALaDerecha = 0;
        for (Colocado colocado : nodos) {
            masALaDerecha = Math.max(masALaDerecha, colocado.x());
        }
        redimensionar(masALaDerecha + ANCHO_CAJA / 2 + MARGEN,
                MARGEN * 2 + (altura - 1) * PASO_NIVEL + ALTO_CAJA);
        GraphicsContext pincel = limpiar();

        // Primero todas las lineas y despues todas las cajas: si se alternaran, las lineas de un
        // nodo cruzarian por encima de las cajas ya pintadas.
        pincel.setStroke(BORDE);
        pincel.setLineWidth(2);
        for (double[] linea : lineas) {
            pincel.strokeLine(linea[0], linea[1], linea[2], linea[3]);
        }
        for (Colocado nodoColocado : nodos) {
            caja(pincel, nodoColocado.x(), nodoColocado.y(),
                    nodoColocado.etiqueta(), nodoColocado.resaltado());
        }
    }

    /**
     * Coloca el nodo sobre sus hijos, reservando sitio nivel por nivel.
     *
     * <p><b>Solo se separan los nodos que comparten nivel.</b> Dar a cada nodo una columna propia
     * en todo el arbol —una por posicion del recorrido inorden— tambien evita los solapes, pero
     * deja el dibujo larguisimo: con 17 canciones salian 2000 px de ancho casi vacios. Aqui cada
     * nivel lleva su propia marca de "hasta donde esta ocupado", asi que un nodo del nivel 8 puede
     * ir justo debajo de otro del nivel 2 sin problema, y el ancho total pasa a depender del nivel
     * mas poblado, no del numero de canciones.</p>
     *
     * <p>Cada padre se centra sobre sus hijos; si eso lo dejara encima de un hermano ya colocado,
     * se corre a la derecha lo justo. Un nodo con un solo hijo se desplaza media caja hacia el lado
     * contrario, que es lo que hace visible si la rama que falta es la izquierda o la derecha.</p>
     *
     * @param siguienteLibre primera x libre de cada nivel; se va actualizando al colocar
     * @return la x del centro de este nodo, o -1 si la rama esta vacia
     */
    private double colocar(EstructuraVisual.Rama rama, int nivel, double[] siguienteLibre,
                           List<Colocado> nodos, List<double[]> lineas, String actual) {
        if (rama == null) {
            return -1;
        }
        double xIzquierdo = colocar(rama.izquierdo(), nivel + 1, siguienteLibre,
                nodos, lineas, actual);
        double xDerecho = colocar(rama.derecho(), nivel + 1, siguienteLibre,
                nodos, lineas, actual);

        double x;
        if (xIzquierdo < 0 && xDerecho < 0) {
            x = siguienteLibre[nivel];
        } else if (xIzquierdo < 0) {
            x = xDerecho - PASO_COLUMNA / 2;
        } else if (xDerecho < 0) {
            x = xIzquierdo + PASO_COLUMNA / 2;
        } else {
            x = (xIzquierdo + xDerecho) / 2;
        }
        x = Math.max(x, siguienteLibre[nivel]);
        siguienteLibre[nivel] = x + PASO_COLUMNA;

        double y = MARGEN + nivel * PASO_NIVEL;
        double yHijos = MARGEN + (nivel + 1) * PASO_NIVEL;
        if (xIzquierdo >= 0) {
            lineas.add(new double[] {x, y + ALTO_CAJA, xIzquierdo, yHijos});
        }
        if (xDerecho >= 0) {
            lineas.add(new double[] {x, y + ALTO_CAJA, xDerecho, yHijos});
        }
        nodos.add(new Colocado(rama.etiqueta(), x, y, rama.etiqueta().equals(actual)));
        return x;
    }

    /** @return cuantos nodos cuelgan de esta rama, ella incluida */
    private static int contar(EstructuraVisual.Rama rama) {
        if (rama == null) {
            return 0;
        }
        return 1 + contar(rama.izquierdo()) + contar(rama.derecho());
    }

    // ------------------------------------------------------------------
    // Piezas comunes
    // ------------------------------------------------------------------

    /**
     * Una caja con su etiqueta, resaltada si es la cancion en curso.
     *
     * @param x centro horizontal de la caja
     * @param y borde superior de la caja
     */
    private void caja(GraphicsContext pincel, double x, double y,
                      String etiqueta, boolean resaltada) {
        double izquierda = x - ANCHO_CAJA / 2;
        pincel.setFill(CAJA);
        pincel.fillRect(izquierda, y, ANCHO_CAJA, ALTO_CAJA);
        pincel.setStroke(resaltada ? RESALTE : BORDE);
        pincel.setLineWidth(resaltada ? 3 : 2);
        pincel.strokeRect(izquierda, y, ANCHO_CAJA, ALTO_CAJA);

        pincel.setFill(resaltada ? RESALTE : TEXTO);
        pincel.fillText(recortar(etiqueta), izquierda + 6, y + ALTO_CAJA / 2 + 4);
    }

    private void mensajeVacio(GraphicsContext pincel, String texto) {
        leyenda.setText("");
        pincel.setFill(APAGADO);
        pincel.fillText(texto, lienzo.getWidth() / 2 - texto.length() * 3,
                lienzo.getHeight() / 2);
    }

    /**
     * Recorta la etiqueta a lo que de verdad cabe en la caja.
     *
     * <p>Se mide el texto en vez de contar caracteres: con una fuente de ancho fijo la cuenta
     * seria equivalente, pero si la fuente pixel no esta instalada JavaFX cae en otra de ancho
     * variable y una cuenta fija dejaria los titulos cortados a media palabra o desbordados.</p>
     */
    private String recortar(String texto) {
        if (texto == null) {
            return "";
        }
        double disponible = ANCHO_CAJA - 12;
        if (ancho(texto) <= disponible) {
            return texto;
        }
        int corte = texto.length();
        while (corte > 1 && ancho(texto.substring(0, corte) + "…") > disponible) {
            corte--;
        }
        return texto.substring(0, corte) + "…";
    }

    private double ancho(String texto) {
        regla.setText(texto);
        return regla.getLayoutBounds().getWidth();
    }
}
