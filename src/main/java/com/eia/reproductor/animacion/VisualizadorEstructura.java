package com.eia.reproductor.animacion;

import com.eia.reproductor.modelo.EstructuraVisual;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dibuja la estructura de datos que el modo activo tiene cargada en este momento. */
public class VisualizadorEstructura {
    /** Lo que se ve sin desplazarse. */
    private static final double ANCHO_VISIBLE = 760;
    private static final double ALTO_VISIBLE = 400;

    private static final double ANCHO_CAJA = 116;
    private static final double ALTO_CAJA = 30;

    /** Distancia entre columnas del arbol. */
    private static final double PASO_COLUMNA = ANCHO_CAJA + 10;
    private static final double PASO_NIVEL = 52;
    private static final double MARGEN = 20;

    /** Lo que hay que dejar libre para el marco, la barra de titulo y la leyenda de abajo. */
    private static final double MARGEN_PANTALLA_ANCHO = 120;
    private static final double MARGEN_PANTALLA_ALTO = 200;

    /** Los colores del dibujo, que hay que repetir aqui y no en la hoja de estilos. */
    private record Paleta(Color fondo, Color caja, Color borde, Color texto,
                          Color resalte, Color apagado) { }

    private static final Paleta OSCURA = new Paleta(
            Color.web("#0D0D14"), Color.web("#161620"), Color.web("#296287"),
            Color.web("#E6F5F8"), Color.web("#4FC3E8"), Color.web("#5A6472"));

    private static final Paleta CLARA = new Paleta(
            Color.web("#16171c"), Color.web("#22242c"), Color.web("#2f6fb5"),
            Color.web("#f0f0f0"), Color.web("#d3323f"), Color.web("#6b7280"));

    /** La que toca segun el tema; se fija al empezar a pintar. */
    private Paleta paleta = OSCURA;

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
        // Color inicial; al pintar se cambia por el que toque segun el tema.
        marco.setStyle("-fx-background: " + aHexadecimal(OSCURA.fondo())
                + "; -fx-background-color: " + aHexadecimal(OSCURA.fondo()) + ";");

        nodo.setAlignment(Pos.CENTER);
        nodo.getStyleClass().add("cuerpo-dialogo");
        nodo.getChildren().addAll(titulo, marco, leyenda);
    }

    /** @return el nodo para colgarlo de una escena */
    public VBox nodo() {
        return nodo;
    }

    /** Repinta el panel con el estado actual de la estructura. */
    public void mostrar(EstructuraVisual estructura) {
        if (estructura == null) {
            return;
        }
        titulo.setText("• " + estructura.nombre().toUpperCase());
        elegirPaleta();

        // Cada estructura dimensiona el lienzo antes de pintar: lo que necesita el anillo no tiene
        // nada que ver con lo que necesita un arbol de veinte canciones.
        switch (estructura) {
            case EstructuraVisual.Anillo anillo -> dibujarAnillo(anillo);
            case EstructuraVisual.Cola cola -> dibujarCola(cola);
            case EstructuraVisual.Arbol arbol -> dibujarArbol(arbol);
        }
    }

    /** Ajusta el lienzo y el hueco por el que se ve. */
    private void redimensionar(double ancho, double alto) {
        lienzo.setWidth(Math.max(ancho, ANCHO_VISIBLE));
        lienzo.setHeight(Math.max(alto, ALTO_VISIBLE));

        Rectangle2D pantalla = Screen.getPrimary().getVisualBounds();
        marco.setPrefViewportWidth(
                Math.min(lienzo.getWidth(), pantalla.getWidth() - MARGEN_PANTALLA_ANCHO));
        marco.setPrefViewportHeight(
                Math.min(lienzo.getHeight(), pantalla.getHeight() - MARGEN_PANTALLA_ALTO));
    }

    /** Mira el tema de la ventana y elige la paleta. */
    private void elegirPaleta() {
        boolean claro = nodo.getScene() != null
                && nodo.getScene().getRoot().getStyleClass().contains("tema-claro");
        paleta = claro ? CLARA : OSCURA;
        marco.setStyle("-fx-background: " + aHexadecimal(paleta.fondo())
                + "; -fx-background-color: " + aHexadecimal(paleta.fondo()) + ";");
    }

    /** El estilo en linea del ScrollPane necesita el color como texto, no como objeto. */
    private static String aHexadecimal(Color color) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    /** Deja el lienzo del color de fondo y devuelve el pincel listo para dibujar. */
    private GraphicsContext limpiar() {
        GraphicsContext pincel = lienzo.getGraphicsContext2D();
        pincel.setFill(paleta.fondo());
        pincel.fillRect(0, 0, lienzo.getWidth(), lienzo.getHeight());
        pincel.setFont(LETRA);
        return pincel;
    }

    // --- Lista circular doble ---

    /** Dibuja el anillo en circulo, con el cursor en el centro. */
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
        pincel.setStroke(paleta.apagado());
        pincel.setLineWidth(2);
        pincel.strokeOval(centroX - radio, centroY - radio, radio * 2, radio * 2);

        // La aguja del cursor se pinta antes que las cajas para que quede por debajo de ellas.
        if (anillo.indiceActual() >= 0 && anillo.indiceActual() < visibles) {
            double angulo = 2 * Math.PI * anillo.indiceActual() / visibles - Math.PI / 2;
            pincel.setStroke(paleta.resalte());
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
            pincel.setFill(paleta.apagado());
            pincel.fillText("y " + (cuantos - visibles) + " más en el anillo",
                    12, lienzo.getHeight() - 10);
        }
        if (anillo.indiceActual() >= 0) {
            // Sobre el centro, con la aguja saliendo hacia la cancion que suena: sin la linea,
            // la palabra quedaba flotando sin senialar nada.
            pincel.setFill(paleta.resalte());
            pincel.fillText("CURSOR", centroX - 24, centroY - 8);
        }
    }

    // --- Cola FIFO ---

    private void dibujarCola(EstructuraVisual.Cola cola) {
        leyenda.setText("Entra por la derecha y sale por la izquierda. Las que ya sonaron "
                + "salieron de la cola de verdad: no se quedan con un índice apuntándolas.");

        redimensionar(ANCHO_VISIBLE, ALTO_VISIBLE);
        GraphicsContext pincel = limpiar();
        double medio = lienzo.getHeight() / 2;
        pincel.setFill(paleta.texto());
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
            pincel.setFill(paleta.resalte());
            pincel.fillText("FRENTE", inicioX - ANCHO_CAJA / 2, medio + 34);
            if (cuantos > visibles) {
                // Debajo de la ultima caja y no a su derecha: ahi se salia del lienzo y el texto
                // aparecia cortado a media palabra.
                pincel.setFill(paleta.apagado());
                pincel.fillText("+" + (cuantos - visibles) + " esperando",
                        inicioX + (visibles - 1) * PASO_COLUMNA - ANCHO_CAJA / 2, medio + 34);
            }
        }
        pincel.setFill(paleta.apagado());
        pincel.fillText("ya salieron de la cola: " + cola.yaSalieron(),
                12, lienzo.getHeight() - 10);
    }

    // --- Arbol binario de busqueda ---

    /** Un nodo ya colocado, para poder pintar todas las lineas antes que todas las cajas. */
    private record Colocado(String etiqueta, double x, double y, boolean resaltado) { }

    /** Dibuja el arbol colocando cada subarbol entero y apartandolos solo lo justo. */
    private void dibujarArbol(EstructuraVisual.Arbol arbol) {
        if (arbol.raiz() == null) {
            redimensionar(ANCHO_VISIBLE, ALTO_VISIBLE);
            mensajeVacio(limpiar(), "El árbol está vacío");
            return;
        }
        int cuantos = contar(arbol.raiz());
        int altura = arbol.raiz().altura();
        // Se habla del hijo respecto de SU padre, no de barrer el dibujo con la vista: en un
        // dibujo compacto un nieto del subárbol derecho puede quedar más a la izquierda que su
        leyenda.setText("De cada canción cuelgan a la izquierda las anteriores alfabéticamente y "
                + "a la derecha las posteriores. " + cuantos + " canciones, altura " + altura
                + ". Recorrer el árbol por la izquierda, la raíz y la derecha da el orden en que "
                + "suenan. Si entran ya ordenadas se convierte en una sola rama y todo pasa a "
                + "ser O(n).");

        // Las coordenadas salen relativas y pueden ser negativas: el subarbol izquierdo de la raiz
        // crece hacia la izquierda del origen. Se corren todas para que la de mas a la izquierda
        Disposicion disposicion = disponer(arbol.raiz(), 0, arbol.actual());
        double masALaIzquierda = Double.MAX_VALUE;
        double masALaDerecha = 0;
        for (Colocado colocado : disposicion.nodos()) {
            masALaIzquierda = Math.min(masALaIzquierda, colocado.x());
            masALaDerecha = Math.max(masALaDerecha, colocado.x());
        }
        disposicion = desplazar(disposicion, MARGEN + ANCHO_CAJA / 2 - masALaIzquierda);

        redimensionar(masALaDerecha - masALaIzquierda + ANCHO_CAJA + MARGEN * 2,
                MARGEN * 2 + (altura - 1) * PASO_NIVEL + ALTO_CAJA);
        GraphicsContext pincel = limpiar();

        List<Colocado> nodos = disposicion.nodos();
        List<double[]> lineas = disposicion.lineas();

        // Primero todas las lineas y despues todas las cajas: si se alternaran, las lineas de un
        // nodo cruzarian por encima de las cajas ya pintadas.
        pincel.setStroke(paleta.borde());
        pincel.setLineWidth(2);
        for (double[] linea : lineas) {
            escuadra(pincel, linea[0], linea[1], linea[2], linea[3]);
        }
        for (Colocado nodoColocado : nodos) {
            caja(pincel, nodoColocado.x(), nodoColocado.y(),
                    nodoColocado.etiqueta(), nodoColocado.resaltado());
        }
    }

    /** Un subarbol ya colocado, con sus coordenadas todavia relativas. */
    private record Disposicion(List<Colocado> nodos, List<double[]> lineas, double xRaiz) { }

    /** Une padre e hijo con tres tramos en escuadra en vez de una diagonal. */
    private static void escuadra(GraphicsContext pincel,
                                 double xPadre, double yPadre, double xHijo, double yHijo) {
        double yMedio = (yPadre + yHijo) / 2;
        pincel.strokeLine(xPadre, yPadre, xPadre, yMedio);
        pincel.strokeLine(xPadre, yMedio, xHijo, yMedio);
        pincel.strokeLine(xHijo, yMedio, xHijo, yHijo);
    }

    /** Coloca un subarbol entero: primero el izquierdo, luego el derecho, y el padre encima. */
    private Disposicion disponer(EstructuraVisual.Rama rama, int nivel, String actual) {
        if (rama == null) {
            return null;
        }
        Disposicion izquierda = disponer(rama.izquierdo(), nivel + 1, actual);
        Disposicion derecha = disponer(rama.derecho(), nivel + 1, actual);

        if (derecha != null && izquierda != null) {
            derecha = desplazar(derecha, separacionNecesaria(izquierda, derecha));
        }

        double x;
        if (izquierda == null && derecha == null) {
            x = 0;
        } else if (izquierda == null) {
            // Un solo hijo: el padre se corre media caja hacia el lado que falta, que es lo que
            // deja ver de un vistazo si la rama ausente es la izquierda o la derecha.
            x = derecha.xRaiz() - PASO_COLUMNA / 2;
        } else if (derecha == null) {
            x = izquierda.xRaiz() + PASO_COLUMNA / 2;
        } else {
            x = (izquierda.xRaiz() + derecha.xRaiz()) / 2;
        }

        List<Colocado> nodos = new ArrayList<>();
        List<double[]> lineas = new ArrayList<>();
        double y = MARGEN + nivel * PASO_NIVEL;
        double yHijos = MARGEN + (nivel + 1) * PASO_NIVEL;
        for (Disposicion hijo : new Disposicion[] {izquierda, derecha}) {
            if (hijo != null) {
                nodos.addAll(hijo.nodos());
                lineas.addAll(hijo.lineas());
                lineas.add(new double[] {x, y + ALTO_CAJA, hijo.xRaiz(), yHijos});
            }
        }
        nodos.add(new Colocado(rama.etiqueta(), x, y, rama.etiqueta().equals(actual)));
        return new Disposicion(nodos, lineas, x);
    }

    /** Cuanto hay que apartar el subarbol derecho para que no toque al izquierdo. */
    private static double separacionNecesaria(Disposicion izquierda, Disposicion derecha) {
        Map<Double, Double> topeIzquierdo = new HashMap<>();
        for (Colocado nodo : izquierda.nodos()) {
            topeIzquierdo.merge(nodo.y(), nodo.x(), Math::max);
        }
        Map<Double, Double> inicioDerecho = new HashMap<>();
        for (Colocado nodo : derecha.nodos()) {
            inicioDerecho.merge(nodo.y(), nodo.x(), Math::min);
        }

        double desplazamiento = 0;
        for (Map.Entry<Double, Double> nivel : topeIzquierdo.entrySet()) {
            Double empiezaDerecha = inicioDerecho.get(nivel.getKey());
            if (empiezaDerecha != null) {
                desplazamiento = Math.max(desplazamiento,
                        nivel.getValue() + PASO_COLUMNA - empiezaDerecha);
            }
        }
        return desplazamiento;
    }

    /** Mueve un subarbol entero: sus nodos y las lineas que ya traia. */
    private static Disposicion desplazar(Disposicion disposicion, double dx) {
        if (dx == 0) {
            return disposicion;
        }
        List<Colocado> nodos = new ArrayList<>(disposicion.nodos().size());
        for (Colocado nodo : disposicion.nodos()) {
            nodos.add(new Colocado(nodo.etiqueta(), nodo.x() + dx, nodo.y(), nodo.resaltado()));
        }
        List<double[]> lineas = new ArrayList<>(disposicion.lineas().size());
        for (double[] linea : disposicion.lineas()) {
            lineas.add(new double[] {linea[0] + dx, linea[1], linea[2] + dx, linea[3]});
        }
        return new Disposicion(nodos, lineas, disposicion.xRaiz() + dx);
    }

    /** @return cuantos nodos cuelgan de esta rama, ella incluida */
    private static int contar(EstructuraVisual.Rama rama) {
        if (rama == null) {
            return 0;
        }
        return 1 + contar(rama.izquierdo()) + contar(rama.derecho());
    }

    // --- Piezas comunes ---

    /** Una caja con su etiqueta, resaltada si es la cancion en curso. */
    private void caja(GraphicsContext pincel, double x, double y,
                      String etiqueta, boolean resaltada) {
        double izquierda = x - ANCHO_CAJA / 2;
        pincel.setFill(paleta.caja());
        pincel.fillRect(izquierda, y, ANCHO_CAJA, ALTO_CAJA);
        pincel.setStroke(resaltada ? paleta.resalte() : paleta.borde());
        pincel.setLineWidth(resaltada ? 3 : 2);
        pincel.strokeRect(izquierda, y, ANCHO_CAJA, ALTO_CAJA);

        pincel.setFill(resaltada ? paleta.resalte() : paleta.texto());
        pincel.fillText(recortar(etiqueta), izquierda + 6, y + ALTO_CAJA / 2 + 4);
    }

    private void mensajeVacio(GraphicsContext pincel, String texto) {
        leyenda.setText("");
        pincel.setFill(paleta.apagado());
        pincel.fillText(texto, lienzo.getWidth() / 2 - texto.length() * 3,
                lienzo.getHeight() / 2);
    }

    /** Recorta la etiqueta a lo que de verdad cabe en la caja. */
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
