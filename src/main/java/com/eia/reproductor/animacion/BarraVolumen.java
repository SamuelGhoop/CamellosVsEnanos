package com.eia.reproductor.animacion;

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

/**
 * Control de volumen dibujado con los sprites del reproductor.
 *
 * <p>Se compone de tres piezas —tapa izquierda, tramo central y tapa derecha— con dos versiones
 * cada una: verde para lo lleno y gris para lo vacio. El tramo central se pinta con un patron que
 * repite el sprite, de modo que la barra se estira sin deformar el pixel art.</p>
 *
 * <p><b>Como se lee.</b> La parte verde es el nivel; el resto queda gris. Las tapas siguen la misma
 * regla: la izquierda se enciende en cuanto hay algo de volumen y la derecha solo al llegar al
 * maximo. En cero <i>todo</i> queda apagado y el icono cambia a mudo, que es la senial mas rapida
 * de "no vas a oir nada".</p>
 *
 * <p>No sabe nada de audio: avisa del nivel por un {@link IntConsumer} y quien lo reciba decide que
 * hacer. Asi este paquete sigue sin depender de los servicios.</p>
 */
public class BarraVolumen {

    /**
     * Alto en pantalla: los sprites miden 104 px y se muestran a un cuarto.
     *
     * <p>A media escala la barra robaba altura a la lista de proximas canciones, que es lo que de
     * verdad hay que ver. Un cuarto la deja del alto de los botones de transporte, con los que
     * comparte fila.</p>
     */
    private static final double ALTO = 24;

    /** Ancho de las tapas: proporcional a los 28 px del sprite. */
    private static final double ANCHO_TAPA = 6;

    /**
     * Ancho del tramo central; el total de la barra son estos mas las dos tapas.
     *
     * <p>Con 130 la fila de transporte se desbordaba por el borde derecho del panel. Este es el
     * punto medio: se lee bien y cabe junto a los tres botones.</p>
     */
    private static final double ANCHO_CENTRO = 96;

    /** Tamano del icono de altavoz, en proporcion al sprite de 66x42. */
    private static final double ANCHO_ICONO = 24;
    private static final double ALTO_ICONO = 15;

    /**
     * Lado del recuadro del altavoz.
     *
     * <p>Se fija cuadrado a mano porque {@code HBox} rellena el alto de sus hijos por defecto
     * ({@code fillHeight}), y el recuadro se estiraba hasta el alto de los botones de transporte:
     * quedaba un rectangulo alto y estrecho con el icono perdido en el medio.</p>
     */
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
        icono.setCursor(Cursor.HAND);
        // Pulsar el altavoz calla y devuelve el sonido, el gesto de siempre.
        icono.setOnMouseClicked(evento -> alternarMudo());

        // Los dos tramos centrales se apilan a la izquierda: el lleno crece por encima del vacio.
        StackPane centro = new StackPane(centroVacio, centroLleno);
        centro.setAlignment(Pos.CENTER_LEFT);
        centro.setPrefWidth(ANCHO_CENTRO);

        HBox barra = new HBox(tapaIzquierda, centro, tapaDerecha);
        barra.setAlignment(Pos.CENTER);
        barra.setCursor(Cursor.HAND);
        // Que no crezca de alto con la fila: la referencia es el sprite, no los botones.
        barra.setMaxHeight(ALTO);
        // Se convierte desde coordenadas de pantalla y no se usa evento.getX(): en un evento que
        // burbujea desde un hijo, getX() viene referido al hijo que se pulso, no a la barra. Como
        // el rectangulo verde cambia de ancho al mover el volumen, el hijo bajo el raton cambiaba
        // y las coordenadas saltaban: el volumen se movia dos veces y despues hacia cualquier cosa.
        barra.setOnMousePressed(evento -> ajustarDesde(evento, barra));
        barra.setOnMouseDragged(evento -> ajustarDesde(evento, barra));

        // El sprite del altavoz es negro sobre transparente y sobre el panel negro no se veia.
        // Se le pone detras el mismo recuadro azul de los botones de transporte: es la solucion
        // que ya usaba la interfaz para el triangulo de play, y no obliga a repintar el sprite.
        StackPane recuadroIcono = new StackPane(icono);
        recuadroIcono.getStyleClass().add("caja-altavoz");
        // Los tres a la vez: sin el maximo, el HBox lo estira igual hasta el alto de la fila.
        recuadroIcono.setMinSize(LADO_CAJA_ICONO, LADO_CAJA_ICONO);
        recuadroIcono.setPrefSize(LADO_CAJA_ICONO, LADO_CAJA_ICONO);
        recuadroIcono.setMaxSize(LADO_CAJA_ICONO, LADO_CAJA_ICONO);

        Region aire = new Region();
        aire.setPrefWidth(8);
        nodo.getChildren().addAll(recuadroIcono, aire, barra);
        nodo.setAlignment(Pos.CENTER);

        repintar();
    }

    /** @return el nodo para colgarlo de la escena */
    public HBox nodo() {
        return nodo;
    }

    /**
     * Define a quien avisar cuando el usuario mueve el volumen.
     *
     * @param oyente recibe el nivel de 0 a 100
     */
    public void setAlCambiar(IntConsumer oyente) {
        this.alCambiar = oyente == null ? nivel -> { } : oyente;
    }

    /** @return el nivel actual, de 0 a 100 */
    public int volumen() {
        return volumen;
    }

    /**
     * Fija el nivel sin avisar al oyente.
     *
     * <p>Para reflejar un volumen que se decidio en otro sitio sin provocar un ida y vuelta.</p>
     *
     * @param porcentaje nivel de 0 a 100
     */
    public void mostrarVolumen(int porcentaje) {
        volumen = Math.max(0, Math.min(100, porcentaje));
        repintar();
    }

    // ------------------------------------------------------------------
    // Interaccion
    // ------------------------------------------------------------------

    /**
     * Traduce la posicion del raton a un nivel de volumen.
     *
     * @param evento pulsacion o arrastre sobre la barra
     * @param barra  contenedor de las tres piezas
     */
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

    // ------------------------------------------------------------------
    // Dibujo
    // ------------------------------------------------------------------

    private void repintar() {
        boolean hayVolumen = volumen > 0;
        boolean alMaximo = volumen >= 100;

        icono.setImage(hayVolumen ? imagenSonidoEncendido : imagenSonidoApagado);
        tapaIzquierda.setFill(hayVolumen ? izquierdaActiva : izquierdaApagada);
        // La tapa derecha solo se enciende al tope: es lo que marca que no se puede subir mas.
        tapaDerecha.setFill(alMaximo ? derechaActiva : derechaApagada);

        centroVacio.setFill(medioApagada);
        centroLleno.setFill(medioActiva);
        centroLleno.setWidth(ANCHO_CENTRO * volumen / 100.0);
    }

    /**
     * Envuelve un sprite en un patron que cubre el rectangulo entero.
     *
     * <p><b>Se estira, no se repite.</b> Repetirlo dejaba una costura visible en cada union, porque
     * los bordes del sprite no encajan consigo mismos. Estos sprites son bandas horizontales de
     * color uniforme, asi que estirarlos a lo ancho da exactamente el mismo resultado visual y sin
     * ninguna linea.</p>
     */
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
