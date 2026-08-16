package com.eia.reproductor.animacion;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;

import java.io.InputStream;
import java.util.function.IntConsumer;

/** Control de volumen dibujado con los sprites del reproductor. */
public class BarraVolumen {
    /**
     * Divisor unico para todos los sprites de la barra.
     *
     * <p>Tiene que ser el mismo para el alto y para las tapas, y entero. Antes el alto iba a 24
     * (104/4,33) y las tapas a 6 (28/4,67): al no coincidir las rejillas de pixeles, en la union
     * entre el tramo central y la tapa derecha aparecia una costura.</p>
     */
    private static final int DIVISOR_SPRITE = 4;

    /** Alto en pantalla: los sprites miden 104 px. */
    private static final double ALTO = 104 / DIVISOR_SPRITE;

    /** Ancho de las tapas: los sprites miden 28 px. */
    private static final double ANCHO_TAPA = 28 / DIVISOR_SPRITE;

    /** Ancho del tramo central; con las dos tapas la barra suma 108, que es lo que cabe. */
    private static final double ANCHO_CENTRO = 94;

    /** Margen invisible arriba y abajo de la barra, solo para agrandar la zona de clic. */
    private static final double MARGEN_CLIC = 7;

    /** Tamano del icono de altavoz, en proporcion al sprite de 66x42. */
    private static final double ANCHO_ICONO = 24;
    private static final double ALTO_ICONO = 15;

    /** Lado del recuadro del altavoz. */
    private static final double LADO_CAJA_ICONO = 34;

    private static final String RUTA_IZQUIERDA_ACTIVA = "/imagenes/vol-izquierda-activa.png";
    private static final String RUTA_IZQUIERDA_APAGADA = "/imagenes/vol-izquierda-apagada.png";
    private static final String RUTA_MEDIO_ACTIVA = "/imagenes/vol-medio-activa.png";
    private static final String RUTA_MEDIO_APAGADA = "/imagenes/vol-medio-apagada.png";
    private static final String RUTA_DERECHA_ACTIVA = "/imagenes/vol-derecha-activa.png";
    private static final String RUTA_DERECHA_APAGADA = "/imagenes/vol-derecha-apagada.png";
    private static final String RUTA_SONIDO_ENCENDIDO = "/imagenes/sonido-encendido.png";
    private static final String RUTA_SONIDO_APAGADO = "/imagenes/sonido-apagado.png";

    private final ImageView icono = new ImageView();
    private final Rectangle tapaIzquierda = new Rectangle(ANCHO_TAPA, ALTO);
    private final Rectangle centroLleno = new Rectangle(0, ALTO);
    private final Rectangle centroVacio = new Rectangle(ANCHO_CENTRO, ALTO);
    private final Rectangle tapaDerecha = new Rectangle(ANCHO_TAPA, ALTO);
    private final HBox nodo = new HBox();

    private final Image imagenSonidoEncendido = cargar(RUTA_SONIDO_ENCENDIDO);
    private final Image imagenSonidoApagado = cargar(RUTA_SONIDO_APAGADO);
    private final ImagePattern izquierdaActiva = patron(RUTA_IZQUIERDA_ACTIVA);
    private final ImagePattern izquierdaApagada = patron(RUTA_IZQUIERDA_APAGADA);
    private final ImagePattern medioActiva = patron(RUTA_MEDIO_ACTIVA);
    private final ImagePattern medioApagada = patron(RUTA_MEDIO_APAGADA);
    private final ImagePattern derechaActiva = patron(RUTA_DERECHA_ACTIVA);
    private final ImagePattern derechaApagada = patron(RUTA_DERECHA_APAGADA);

    private IntConsumer alCambiar = nivel -> { };
    private int volumen = 100;

    /** Nivel al que vuelve el altavoz si se pulsa estando en cero. */
    private int volumenAntesDeCallar = 100;

    /** Construye el control al maximo, que es como arranca el reproductor. */
    public BarraVolumen() {
        icono.setFitWidth(ANCHO_ICONO);
        icono.setFitHeight(ALTO_ICONO);
        icono.setSmooth(false);
        icono.setMouseTransparent(true);

        // Los dos tramos centrales se apilan a la izquierda: el lleno crece por encima del vacio.
        StackPane centro = new StackPane(centroVacio, centroLleno);
        centro.setAlignment(Pos.CENTER_LEFT);
        centro.setPrefWidth(ANCHO_CENTRO);

        HBox barra = new HBox(tapaIzquierda, centro, tapaDerecha);
        barra.setAlignment(Pos.CENTER);
        // Que no crezca de alto con la fila: la referencia es el sprite, no los botones.
        barra.setMaxHeight(ALTO);
        barra.setMouseTransparent(true);

        // La barra mide 26 px de alto: apuntarle es incomodo. Se envuelve en una zona con margen
        // invisible que recibe los clics por ella. setPickOnBounds hace que cuente todo el
        // rectangulo, incluido el margen, y no solo donde hay algo pintado.
        StackPane zonaClic = new StackPane(barra);
        // El margen es solo arriba y abajo. A los lados sobra —la barra ya mide 108 px de ancho y
        // acertarle nunca fue el problema— y ademas correria la barra dejando un hueco a la
        // derecha.
        zonaClic.setPadding(new Insets(MARGEN_CLIC, 0, MARGEN_CLIC, 0));
        zonaClic.setPickOnBounds(true);
        zonaClic.setCursor(Cursor.HAND);
        // Se convierte desde coordenadas de pantalla y no se usa evento.getX(): en un evento que
        // burbujea desde un hijo, getX() viene referido al hijo que se pulso, no a la barra.
        // Pulsar en el margen da una x fuera de rango, y ajustarDesde la recorta a 0 o a 100.
        zonaClic.setOnMousePressed(evento -> ajustarDesde(evento, barra));
        zonaClic.setOnMouseDragged(evento -> ajustarDesde(evento, barra));

        // El sprite del altavoz es negro sobre transparente y sobre el panel negro no se veia.
        // Se le pone detras el mismo recuadro azul de los botones de transporte: es la solucion
        StackPane recuadroIcono = new StackPane(icono);
        recuadroIcono.getStyleClass().add("caja-altavoz");
        // Los tres a la vez: sin el maximo, el HBox lo estira igual hasta el alto de la fila.
        recuadroIcono.setMinSize(LADO_CAJA_ICONO, LADO_CAJA_ICONO);
        recuadroIcono.setPrefSize(LADO_CAJA_ICONO, LADO_CAJA_ICONO);
        recuadroIcono.setMaxSize(LADO_CAJA_ICONO, LADO_CAJA_ICONO);
        // El clic lo atiende el recuadro entero, no el dibujo. Un ImageView solo recibe el raton
        // donde el sprite tiene pixeles opacos, y el altavoz es una figura pequenia rodeada de
        // transparencia: habia que acertarle al dibujo para que el boton respondiera.
        recuadroIcono.setPickOnBounds(true);
        recuadroIcono.setCursor(Cursor.HAND);
        // Pulsar el altavoz calla y devuelve el sonido, el gesto de siempre.
        recuadroIcono.setOnMouseClicked(evento -> alternarMudo());

        Region aire = new Region();
        aire.setPrefWidth(8);
        nodo.getChildren().addAll(recuadroIcono, aire, zonaClic);
        nodo.setAlignment(Pos.CENTER);

        repintar();
    }

    /** @return el nodo para colgarlo de la escena */
    public HBox nodo() {
        return nodo;
    }

    /** Define a quien avisar cuando el usuario mueve el volumen. */
    public void setAlCambiar(IntConsumer oyente) {
        this.alCambiar = oyente == null ? nivel -> { } : oyente;
    }

    /** @return el nivel actual, de 0 a 100 */
    public int volumen() {
        return volumen;
    }

    /** Fija el nivel sin avisar al oyente. */
    public void mostrarVolumen(int porcentaje) {
        volumen = Math.max(0, Math.min(100, porcentaje));
        repintar();
    }

    // --- Interaccion ---

    /** Traduce la posicion del raton a un nivel de volumen. */
    private void ajustarDesde(MouseEvent evento, HBox barra) {
        double ancho = barra.getWidth();
        if (ancho <= 0) {
            return;
        }
        double x = barra.screenToLocal(evento.getScreenX(), evento.getScreenY()).getX();
        cambiarA((int) Math.round(Math.max(0, Math.min(1, x / ancho)) * 100));
    }

    private void alternarMudo() {
        if (volumen > 0) {
            volumenAntesDeCallar = volumen;
            cambiarA(0);
        } else {
            cambiarA(volumenAntesDeCallar == 0 ? 100 : volumenAntesDeCallar);
        }
    }

    private void cambiarA(int porcentaje) {
        int limitado = Math.max(0, Math.min(100, porcentaje));
        if (limitado == volumen) {
            return;
        }
        volumen = limitado;
        repintar();
        alCambiar.accept(volumen);
    }

    // --- Dibujo ---

    private void repintar() {
        boolean hayVolumen = volumen > 0;
        boolean alMaximo = volumen >= 100;

        icono.setImage(hayVolumen ? imagenSonidoEncendido : imagenSonidoApagado);
        tapaIzquierda.setFill(hayVolumen ? izquierdaActiva : izquierdaApagada);
        // La tapa derecha solo se enciende al tope: es lo que marca que no se puede subir mas.
        tapaDerecha.setFill(alMaximo ? derechaActiva : derechaApagada);

        centroVacio.setFill(medioApagada);
        centroLleno.setFill(medioActiva);
        // Redondeado a pixel entero: con un ancho fraccionario, JavaFX suaviza el borde derecho
        // del relleno y ese difuminado se ve como una raya en mitad de la barra.
        centroLleno.setWidth(Math.round(ANCHO_CENTRO * volumen / 100.0));
    }

    /** Envuelve un sprite en un patron que cubre el rectangulo entero. */
    private static ImagePattern patron(String ruta) {
        Image imagen = cargar(ruta);
        if (imagen == null) {
            return new ImagePattern(new javafx.scene.image.WritableImage(1, 1));
        }
        // Coordenadas proporcionales (0 a 1): una sola copia estirada al tamanio del rectangulo.
        return new ImagePattern(imagen, 0, 0, 1, 1, true);
    }

    private static Image cargar(String ruta) {
        try (InputStream flujo = BarraVolumen.class.getResourceAsStream(ruta)) {
            return flujo == null ? null : new Image(flujo);
        } catch (Exception noSePudoCargar) {
            return null;
        }
    }
}
