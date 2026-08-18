package com.eia.reproductor;

import com.eia.reproductor.animacion.AjustesAnimacion;
import com.eia.reproductor.animacion.SpriteAnimado;
import javafx.animation.FadeTransition;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.InputStream;
import java.net.URL;
import java.util.Random;

/**
 * Presentacion animada que se ve mientras la aplicacion arranca.
 *
 * <p>Sustituye a la ventanita de "CARGANDO": la mascota baja colgada de su telarania, el logo
 * entra de golpe con un fogonazo y se llena la barra de carga, todo con el tema en 8 bits de
 * fondo. Lo que dura sale de {@link #duracion()}, sumado sobre los actos
 * de verdad.</p>
 *
 * <p><b>Se puede saltar.</b> Un clic o cualquier tecla la corta y entra directo a la aplicacion.
 * Sin eso seria insufrible para quien abre el programa veinte veces seguidas.</p>
 *
 * <p><b>Por que la ventana principal se construye por debajo.</b> Montarla bloquea el hilo grafico
 * casi un segundo. Se hace en el hueco de la primera fase, cuando en pantalla solo hay un fundido
 * lento y un tiron no se nota; el resto de la animacion corre ya sin nada pesado al lado.</p>
 */
public final class IntroDeArranque {
    /** Area de animacion deseada. Si no cabe en la pantalla se recorta, nunca el marco. */
    private static final double ANCHO_DESEADO = 900;
    private static final double ALTO_DESEADO = 640;

    /** Area minima por debajo de la cual el guion deja de tener sentido. */
    private static final double ANCHO_MINIMO = 520;
    private static final double ALTO_MINIMO = 380;

    /** Aire que se le deja a la pantalla para no ocuparla de borde a borde. */
    private static final double MARGEN_PANTALLA = 40;

    /**
     * Grosor del marco, tomado del tamanio real de los sprites.
     *
     * <p>Se dibuja a escala 1. Es la unica escala entera que cabe: a 2 el marco se comeria 316 de
     * los 900 px de ancho. Y reducirlo no es opcion —a media escala el pixel art se emborrona—,
     * asi que en vez de encoger el marco, la ventana crece para alojarlo y el area de animacion se
     * queda intacta en 900x640.</p>
     */
    private static final double MARCO_LADO = 79;
    private static final double MARCO_ARRIBA = 79;
    private static final double MARCO_ABAJO = 42;

    /**
     * Ritmo base de los cuatro actos, en milisegundos.
     *
     * <p>No son las duraciones definitivas: son las <b>proporciones</b>. El {@link Compas} las
     * estira o encoge todas por el mismo factor para que la presentacion acabe con la pista, y por
     * eso el reparto entre actos se mantiene sea cual sea la cancion.</p>
     */
    static final double BASE_ACTO_UNO = 1400;
    static final double BASE_ACTO_DOS = 970;
    static final double BASE_ACTO_TRES = 2600;
    static final double BASE_ACTO_CUATRO = 700;

    /**
     * El desgarro de senial que va entre el acto 1 y el acto 2.
     *
     * <p>Se suma al ritmo base en vez de robarle tiempo a los actos: los cuatro conservan entre si
     * exactamente la misma relacion que tenian (1400 : 970 : 2600 : 700).</p>
     *
     * <p><b>Dos rafagas cortas, no una larga.</b> Una sola sacudida, por breve que fuera, se leia
     * como que algo se habia roto. Partida en dos chispazos con un respiro en medio se lee como lo
     * que es: una senial que titila antes de estabilizarse. Cada rafaga dura la mitad que la
     * anterior version entera.</p>
     */
    static final double BASE_GLITCH_RAFAGA = 130;

    /** El respiro entre las dos rafagas: la imagen se ve limpia un instante. */
    static final double BASE_GLITCH_PAUSA = 110;

    /** Lo que ocupa el desgarro completo dentro del ritmo base. */
    static final double BASE_GLITCH =
            BASE_GLITCH_RAFAGA * 2 + BASE_GLITCH_PAUSA;

    static final double BASE_TOTAL = BASE_ACTO_UNO + BASE_GLITCH + BASE_ACTO_DOS
            + BASE_ACTO_TRES + BASE_ACTO_CUATRO;

    /** Cuantas bandas horizontales parten la imagen durante el desgarro. */
    private static final int BANDAS_GLITCH = 24;

    /** Cada cuanto se recalculan las bandas. 33 ms son unos 30 cuadros por segundo. */
    private static final double MS_POR_CUADRO = 33;

    /**
     * Desplazamiento maximo de una banda, como fraccion del ancho.
     *
     * <p>Va en fraccion y no en pixeles para que el desgarro se vea igual de fuerte tanto en el
     * area de 900 px como en la recortada de 826 de una pantalla pequenia.</p>
     *
     * <p>Bajada del 6 % al 4 %: con 54 px de salto el contenido se despedazaba y costaba reconocer
     * lo que habia debajo. Con 36 la franja salta lo justo para que se note el corte.</p>
     */
    static final double AMPLITUD_GLITCH = 0.04;

    /**
     * Cuantas bandas se quedan quietas en cada cuadro, de 0 a 255.
     *
     * <p>Si se movieran todas se leeria como ruido de television. Con 90 se movia el 66 % y era
     * justo eso: puro caos. Con 150 se mueve el 41 %, asi que la mayoria de las franjas aguantan
     * quietas y las pocas que saltan se leen como cortes en la senial.</p>
     */
    private static final int UMBRAL_QUIETO = 150;

    /**
     * Los tres tiempos del encendido del tubo, en milisegundos.
     *
     * <p>Fijos, no proporcionales: el chasquido de un televisor encendiendose dura lo que dura, y
     * estirarlo al ritmo de la cancion lo convertiria en otra cosa.</p>
     */
    private static final double MS_PUNTO = 120;
    private static final double MS_APERTURA = 350;
    private static final double MS_ASENTAMIENTO = 130;
    static final double MS_ENCENDIDO = MS_PUNTO + MS_APERTURA + MS_ASENTAMIENTO;

    /** Grosor de la linea de barrido y del filo que se retira con cada persiana. */
    private static final double GROSOR_BARRIDO = 2;

    /** Cuanto sube el brillo en el parpadeo de asentamiento. */
    private static final double BRILLO_ASENTAMIENTO = 0.15;

    /** Topes de la duracion, para que ni una pista cortisima ni una larga arruinen la entrada. */
    private static final double MIN_SEGUNDOS = 4;
    private static final double MAX_SEGUNDOS = 10;

    /** Lo que se espera al Media antes de arrancar sin el; si tarda mas, se tira de proporciones. */
    private static final Duration ESPERA_MEDIA = Duration.millis(600);

    private static final String RUTA_MUSICA = "/audio/intro-8bit.mp3";
    private static final String RUTA_TITULO = "/imagenes/spidey/titulo.png";
    private static final String RUTA_ARANIA = "/imagenes/spidey/fondo-arania.png";

    private static final Color AZUL = Color.web("#4FC3E8");
    private static final Color ROJO = Color.web("#d3323f");
    private static final Color FONDO = Color.web("#07070C");

    /** Cuantos hilos de telarania se dibujan de fondo. */
    private static final int HILOS_DE_FONDO = 14;

    /**
     * Cuantas veces mas rapido corre la presentacion al revisar un movimiento suelto.
     *
     * <p>A 8x, llegar al ultimo movimiento cuesta menos de un segundo en vez de los diez de la
     * presentacion entera. No mas: por encima, el hilo grafico se salta pulsos y algunas acciones
     * de los KeyFrames no llegan a dispararse.</p>
     */
    private static final int VELOCIDAD_REVISION = 8;

    /** Ancho del logo en pantalla. El sprite mide 800x133, asi que a 700 queda en 116 de alto. */
    private static final double ANCHO_LOGO = 700;

    /** Grosor del hilo, igualado al que trae dibujado el sprite de la mascota. */
    private static final double GROSOR_HILO = 8;

    /** Cuanto se mece la mascota colgada, y cuanto tarda en ir de un extremo al otro. */
    private static final double GRADOS_VAIVEN = 7;
    private static final Duration PERIODO_VAIVEN = Duration.millis(1300);

    /** Lado del bloque de la barra por pasos, y cuanto avanza de uno al siguiente. */
    private static final double LADO_BLOQUE = 8;
    private static final double PASO_BLOQUE = LADO_BLOQUE + 4;

    /** Cual de las dos barras se muestra: por bloques o el degradado continuo. */
    private static final boolean BARRA_POR_BLOQUES = true;

    /** Area de animacion de esta ejecucion, ya ajustada a la pantalla. */
    private final double ancho;
    private final double alto;
    private final double anchoVentana;
    private final double altoVentana;

    private final Stage escenario = new Stage(StageStyle.TRANSPARENT);
    private final StackPane raiz = new StackPane();

    /** Lo que se sacude en el golpe del logo; la raiz se queda quieta con el marco. */
    private final StackPane contenido = new StackPane();

    /** Donde viven las bandas del desgarro; vacia salvo durante el efecto. */
    private final Pane capaGlitch = new Pane();

    /** Las persianas del encendido y del apagado, por encima de todo lo demas. */
    private final Pane capaEncendido = new Pane();

    private final MarcoPixel marco = new MarcoPixel();
    private final Pane capaTelaranias = new Pane();

    private final ImageView arania = new ImageView();
    private final ImageView logo = new ImageView();
    /**
     * El tramo de telarania que va del techo a la mascota.
     *
     * <p>Mide {@value #GROSOR_HILO} px porque es lo que mide el hilo que el propio sprite lleva
     * dibujado. Antes eran 3 y se veia el empalme: un hilo fino que de golpe se volvia el triple
     * de grueso justo donde empezaba la mascota.</p>
     */
    private final Rectangle hiloPrincipal = new Rectangle(GROSOR_HILO, 0);
    private final Rectangle fogonazo = new Rectangle();
    private final Rectangle velo = new Rectangle();

    /** Las dos persianas del encendido, con su filo blanco, y el barrido que las precede. */
    private final Rectangle filoArriba = new Rectangle();
    private final Rectangle filoAbajo = new Rectangle();
    private final Rectangle barrido = new Rectangle();
    private Pane hojaArriba;
    private Pane hojaAbajo;

    /**
     * Donde se juntan las dos persianas, redondeado a pixel entero.
     *
     * <p>Con un alto impar —761— la mitad cae en 380,5 y los dos rectangulos quedaban en
     * coordenadas fraccionarias: por la union se colaba una rendija de un pixel y se veia el marco
     * a traves de la pantalla supuestamente apagada.</p>
     */
    private final double mitadVentana;

    private final Label rotulo = new Label("CAMELLOS VS ENANOS");
    private final Label estado = new Label("INICIANDO");
    private final Rectangle barraFondo = new Rectangle(420, 14);
    private final Rectangle barraRelleno = new Rectangle(0, 14);
    private final Pane bloques = new Pane();

    private final SpriteAnimado colgante = new SpriteAnimado(
            AjustesAnimacion.RUTA_COLGANTE, AjustesAnimacion.COLGANTE_CUADROS,
            AjustesAnimacion.COLGANTE_ANCHO_CUADRO, AjustesAnimacion.COLGANTE_ALTO_CUADRO,
            2, AjustesAnimacion.COLGANTE_FPS);

    /** Giro de la mascota con el pivote en lo alto, donde la sujeta la telarania. */
    private final javafx.scene.transform.Rotate pivoteArriba =
            new javafx.scene.transform.Rotate(0, 0, 0);

    /** Hilo y mascota juntos: cuelgan del mismo nodo y por eso no pueden despegarse. */
    private final Pane colgado = new Pane();

    private Timeline vaiven;

    private final Musica musica = new Musica(RUTA_MUSICA);

    private SequentialTransition guion;

    /** El reparto de tiempos de esta ejecucion; se conoce al saber cuanto dura la pista. */
    private Compas compas;
    private Runnable alTerminar = () -> { };
    private boolean terminada;

    /** Arma la escena; no la muestra hasta {@link #mostrar()}. */
    public IntroDeArranque() {
        this(Screen.getPrimary().getVisualBounds());
    }

    /**
     * Version que recibe el espacio disponible, para poder probar otras pantallas.
     *
     * @param disponible area util de la pantalla donde va a salir la presentacion
     */
    IntroDeArranque(Rectangle2D disponible) {
        double[] area = areaQueCabe(disponible.getWidth(), disponible.getHeight());
        ancho = area[0];
        alto = area[1];
        anchoVentana = ancho + MARCO_LADO * 2;
        altoVentana = alto + MARCO_ARRIBA + MARCO_ABAJO;
        mitadVentana = Math.round(altoVentana / 2);

        fogonazo.setWidth(anchoVentana);
        fogonazo.setHeight(altoVentana);
        velo.setWidth(anchoVentana);
        velo.setHeight(altoVentana);

        // Las tres medidas y el recorte: el fondo de arania mide mas que la ventana y, sin esto,
        // era el quien decidia el tamanio. La ventana salia cuadrada de 900x900 en vez de la
        // medida que se pide aqui, y en un portatil de 768 px de alto no habria cabido.
        raiz.setMinSize(anchoVentana, altoVentana);
        raiz.setPrefSize(anchoVentana, altoVentana);
        raiz.setMaxSize(anchoVentana, altoVentana);
        raiz.setClip(new Rectangle(anchoVentana, altoVentana));
        // El borde ya no lo pinta el CSS: lo pone el marco de nueve piezas, encima del contenido.
        raiz.setStyle("-fx-background-color: #07070C;");

        contenido.setMinSize(ancho, alto);
        contenido.setPrefSize(ancho, alto);
        contenido.setMaxSize(ancho, alto);
        StackPane.setMargin(contenido,
                new Insets(MARCO_ARRIBA, MARCO_LADO, MARCO_ABAJO, MARCO_LADO));

        // Todo lo que se sacude va dentro de "contenido". La raiz se queda quieta porque lleva el
        // recorte y el marco: moverla en una ventana transparente descubriria una franja vacia en
        // los bordes durante los 240 ms del golpe.
        contenido.getChildren().addAll(
                fondoDeArania(), capaTelaranias, hiloDeBajada(), bloqueCentral());

        capaGlitch.setVisible(false);
        capaGlitch.setMouseTransparent(true);
        capaGlitch.setMinSize(ancho, alto);
        capaGlitch.setPrefSize(ancho, alto);
        capaGlitch.setMaxSize(ancho, alto);
        StackPane.setMargin(capaGlitch,
                new Insets(MARCO_ARRIBA, MARCO_LADO, MARCO_ABAJO, MARCO_LADO));

        hojaArriba = persiana(true);
        hojaAbajo = persiana(false);

        // El barrido va por encima de las persianas: en el primer tiempo la pantalla esta tapada y
        // lo unico que se ve es esa linea creciendo.
        barrido.setWidth(4);
        barrido.setHeight(GROSOR_BARRIDO);
        barrido.setFill(Color.WHITE);
        barrido.setMouseTransparent(true);
        barrido.setY(mitadVentana - GROSOR_BARRIDO / 2);
        // Crece desde el centro hacia los dos lados: la x tiene que seguir al ancho, o el punto
        // se estiraria solo hacia la derecha desde el borde izquierdo.
        barrido.xProperty().bind(
                barrido.widthProperty().negate().add(anchoVentana).divide(2));

        capaEncendido.getChildren().addAll(hojaArriba, hojaAbajo, barrido);
        capaEncendido.setMouseTransparent(true);
        capaEncendido.setMinSize(anchoVentana, altoVentana);
        capaEncendido.setPrefSize(anchoVentana, altoVentana);
        capaEncendido.setMaxSize(anchoVentana, altoVentana);

        raiz.getChildren().addAll(
                contenido, capaGlitch, marco.nodo(), fogonazo, velo, capaEncendido);

        Scene escena = new Scene(raiz);
        escena.setFill(Color.TRANSPARENT);
        escena.setCursor(Cursor.HAND);
        escena.setOnMousePressed(evento -> saltar());
        // Filtro y no setOnKeyPressed: el manejador de la escena solo se dispara si algun nodo
        // tiene el foco, y aqui no lo tiene ninguno —la raiz es un StackPane, que no entra en el
        // recorrido del tabulador—. El filtro ve la tecla en la fase de captura, antes de que
        // importe quien tiene el foco, asi que el atajo funciona siempre.
        escena.addEventFilter(KeyEvent.KEY_PRESSED, evento -> saltar());

        escenario.setScene(escena);
        escenario.setAlwaysOnTop(true);
    }

    /**
     * Decide el area de animacion que cabe en una pantalla dada.
     *
     * <p><b>El marco no se toca.</b> Se dibuja a escala 1 y encogerlo emborronaria el pixel art,
     * asi que cuando la ventana no cabe lo que se recorta es el area de animacion. En una pantalla
     * de 1024x768 el area baja de 900x640 a lo que quede tras descontar marco y margen.</p>
     *
     * <p>Se queda en un metodo estatico y sin estado para poder comprobarlo con medidas de
     * pantallas que no son la de esta maquina.</p>
     *
     * @param anchoUtil ancho aprovechable de la pantalla
     * @param altoUtil  alto aprovechable de la pantalla
     * @return un par {ancho, alto} del area de animacion
     */
    static double[] areaQueCabe(double anchoUtil, double altoUtil) {
        double cabeDeAncho = anchoUtil - MARGEN_PANTALLA - MARCO_LADO * 2;
        double cabeDeAlto = altoUtil - MARGEN_PANTALLA - MARCO_ARRIBA - MARCO_ABAJO;

        // El minimo va al final a proposito: en una pantalla diminuta se prefiere una ventana algo
        // mas grande que la pantalla antes que un area tan chica que el guion no se entienda.
        return new double[] {
                Math.max(ANCHO_MINIMO, Math.min(ANCHO_DESEADO, cabeDeAncho)),
                Math.max(ALTO_MINIMO, Math.min(ALTO_DESEADO, cabeDeAlto))};
    }

    // --- Piezas de la escena ---

    private ImageView fondoDeArania() {
        cargar(RUTA_ARANIA).ifPresent(arania::setImage);
        arania.setPreserveRatio(true);
        arania.setFitHeight(alto * 1.6);
        arania.setSmooth(false);
        arania.setOpacity(0);
        // Desenfocada: es un telon de fondo, no debe competir con el logo.
        arania.setEffect(new GaussianBlur(6));
        return arania;
    }

    /**
     * El hilo del que baja la mascota; crece de arriba hacia abajo.
     *
     * <p>Nace pegado al borde superior del area, sin translateY. Antes llevaba un
     * {@code -alto / 2} heredado de cuando el hilo se colocaba de otra forma, y lo dejaba
     * empezando unos pixeles mas abajo: se veia un tramo sin hilo entre el marco y donde
     * arrancaba, como si la telarania estuviera colgada del aire.</p>
     */
    private Pane hiloDeBajada() {
        hiloPrincipal.setFill(Color.web("#E6F5F8"));
        // Dentro del panel se colocan a mano: el hilo centrado arriba y la mascota colgando de el.
        hiloPrincipal.setLayoutX(colgante.ancho() / 2 - hiloPrincipal.getWidth() / 2);
        hiloPrincipal.setLayoutY(0);
        colgante.nodo().setLayoutX(0);
        colgante.nodo().setLayoutY(0);

        colgado.getChildren().addAll(hiloPrincipal, colgante.nodo());
        colgado.setMinSize(colgante.ancho(), alto);
        colgado.setPrefSize(colgante.ancho(), alto);
        colgado.setMaxSize(colgante.ancho(), alto);
        colgado.setMouseTransparent(true);
        StackPane.setAlignment(colgado, Pos.TOP_CENTER);
        return colgado;
    }

    /** Logo, rotulo y barra de progreso, apilados en el centro. */
    private StackPane bloqueCentral() {
        cargar(RUTA_TITULO).ifPresent(logo::setImage);
        logo.setPreserveRatio(true);
        // 700 de los 900 del area. Por encima de eso empieza a tocar a la mascota, que baja
        // hasta y=256, y al rotulo de abajo.
        logo.setFitWidth(ANCHO_LOGO);
        logo.setSmooth(false);
        logo.setTranslateY(10);
        logo.setOpacity(0);
        logo.setScaleX(6);
        logo.setScaleY(6);

        rotulo.setStyle("-fx-font-family: 'Press Start 2P'; -fx-font-size: 13px;"
                + " -fx-text-fill: #d3323f;");
        rotulo.setOpacity(0);
        rotulo.setTranslateY(80);

        estado.setStyle("-fx-font-family: 'Press Start 2P'; -fx-font-size: 9px;"
                + " -fx-text-fill: #4FC3E8;");
        estado.setOpacity(0);
        estado.setTranslateY(160);

        barraFondo.setFill(Color.web("#101018"));
        barraFondo.setStroke(Color.web("#154F80"));
        barraFondo.setStrokeWidth(3);
        barraFondo.setTranslateY(130);
        barraFondo.setOpacity(0);

        barraRelleno.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, ROJO), new Stop(1, AZUL)));
        barraRelleno.setTranslateY(130);
        barraRelleno.setOpacity(0);
        StackPane.setAlignment(barraRelleno, Pos.CENTER);

        armarBloques();

        StackPane bloque = new StackPane(logo, rotulo, barraFondo, barraRelleno, bloques, estado);
        bloque.setAlignment(Pos.CENTER);
        return bloque;
    }

    // --- Puesta en marcha ---

    /**
     * Muestra la ventana centrada y lanza la presentacion con su musica.
     *
     * <p>La ventana sale de inmediato, pero el guion no arranca hasta saber cuanto dura la pista:
     * es lo que fija el ritmo de los cuatro actos. La espera es de milisegundos y no se ve, porque
     * el primer acto empieza en negro con todo a opacidad cero.</p>
     */
    public void mostrar() {
        colocarPiezas();
        escenario.show();
        centrar();

        musica.cuandoSepaLaDuracion(this::arrancarCon);
    }

    /** Arma el guion al ritmo que marque la pista y lo lanza junto con la musica. */
    private void arrancarCon(Duration duracionPista) {
        if (guion != null || terminada) {
            // Ya arrancó, o el usuario la saltó mientras se resolvia la duracion.
            return;
        }
        compas = new Compas(duracionPista);
        guion = construirGuion(compas);
        guion.setOnFinished(evento -> cerrar());
        musica.arrancar();
        guion.play();
    }

    /** Deja cada sprite en su sitio de partida, ya conocido el tamanio de la ventana. */
    private void colocarPiezas() {
        colgante.nodo().setTranslateY(-colgante.alto());
        colgante.nodo().setOpacity(0);

        fogonazo.setFill(Color.WHITE);
        fogonazo.setOpacity(0);
        fogonazo.setMouseTransparent(true);

        velo.setFill(FONDO);
        velo.setOpacity(0);
        velo.setMouseTransparent(true);

        sembrarTelaranias();
    }

    /**
     * Pone a la mascota a mecerse en el hilo mientras cuelga.
     *
     * <p>El ciclo del sprite ya la anima por dentro, pero el nodo se quedaba clavado en el mismo
     * punto y la telarania parecia un palo. Aqui se le da el vaiven: gira unos grados a un lado y
     * al otro, con el pivote arriba —donde nace el hilo—, de modo que oscila colgando en vez de
     * girar sobre su ombligo. El hilo se inclina con ella.</p>
     *
     * <p>Va en bucle de ida y vuelta y se detiene al cerrar, junto con el resto.</p>
     */
    private void balancear() {
        // Un unico giro para el conjunto, con el pivote en el techo. Antes cada pieza tenia el
        // suyo, y aunque los dos estaban "arriba", no eran el MISMO punto: el del hilo en y=0 y el
        // de la mascota en su propio borde, 8 px mas abajo. Girando por separado, el extremo del
        // hilo se apartaba de la cabeza y quedaba un tramo de cuerda al aire. Como cuelgan juntos
        // de un solo nodo, ahora es imposible que se separen.
        pivoteArriba.setPivotX(colgante.ancho() / 2);
        pivoteArriba.setPivotY(0);
        colgado.getTransforms().add(pivoteArriba);

        vaiven = mecer(pivoteArriba);
    }

    /** Un vaiven de ida y vuelta, indefinido, sobre el angulo de un giro. */
    private static Timeline mecer(javafx.scene.transform.Rotate giro) {
        Timeline ciclo = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(giro.angleProperty(), -GRADOS_VAIVEN)),
                new KeyFrame(PERIODO_VAIVEN,
                        new KeyValue(giro.angleProperty(), GRADOS_VAIVEN, Interpolator.EASE_BOTH)));
        ciclo.setAutoReverse(true);
        ciclo.setCycleCount(Timeline.INDEFINITE);
        ciclo.play();
        return ciclo;
    }

    /**
     * Version alternativa de la barra: bloques sueltos en vez de un degradado continuo.
     *
     * <p>Cada bloque mide {@value #LADO_BLOQUE} px y conserva el paso de rojo a cian, pero a
     * saltos. Se enciende de izquierda a derecha siguiendo el mismo ancho que anima la barra
     * continua, asi que las dos versiones van sincronizadas y cambiar de una a otra es mover
     * {@link #BARRA_POR_BLOQUES}.</p>
     */
    private void armarBloques() {
        bloques.setMouseTransparent(true);
        bloques.setTranslateY(130);
        bloques.setOpacity(0);
        StackPane.setAlignment(bloques, Pos.CENTER);

        double util = barraFondo.getWidth() - 6;
        int cuantos = (int) (util / PASO_BLOQUE);
        double sobra = util - cuantos * PASO_BLOQUE;

        for (int i = 0; i < cuantos; i++) {
            Rectangle ladrillo = new Rectangle(LADO_BLOQUE, 10);
            // El color avanza de rojo a cian igual que el degradado, pero en escalones.
            ladrillo.setFill(ROJO.interpolate(AZUL, cuantos == 1 ? 0 : (double) i / (cuantos - 1)));
            ladrillo.setX(3 + sobra / 2 + i * PASO_BLOQUE);
            ladrillo.setY(2);
            ladrillo.setVisible(false);
            bloques.getChildren().add(ladrillo);
        }

        bloques.setMinSize(barraFondo.getWidth(), 14);
        bloques.setPrefSize(barraFondo.getWidth(), 14);
        bloques.setMaxSize(barraFondo.getWidth(), 14);

        // Se cuelga del ancho de la barra continua: una sola animacion mueve las dos versiones.
        barraRelleno.widthProperty().addListener((observable, anterior, actual) -> {
            int encendidos = (int) Math.round(actual.doubleValue() / util * cuantos);
            for (int i = 0; i < bloques.getChildren().size(); i++) {
                bloques.getChildren().get(i).setVisible(i < encendidos);
            }
        });
    }

    /**
     * Dibuja hilos finos en diagonal, como una telarania de fondo.
     *
     * <p>Nacen invisibles y se encienden por sorteo durante la presentacion, para que el fondo
     * tenga movimiento sin robarle atencion al logo.</p>
     */
    private void sembrarTelaranias() {
        Random azar = new Random(7);
        capaTelaranias.setMouseTransparent(true);
        for (int i = 0; i < HILOS_DE_FONDO; i++) {
            double desde = azar.nextDouble() * ancho;
            Rectangle hilo = new Rectangle(1, alto * 1.6);
            hilo.setFill(Color.web("#4FC3E8", 0.16));
            hilo.setTranslateX(desde);
            hilo.setTranslateY(-alto * 0.3);
            hilo.setRotate(azar.nextBoolean() ? 22 : -22);
            hilo.setOpacity(0);
            capaTelaranias.getChildren().add(hilo);
        }
    }

    // --- El guion ---

    private SequentialTransition construirGuion(Compas compas) {
        return new SequentialTransition(
                encendidoDelTubo(), actoUno(compas), desgarroDeSenial(compas), actoDos(compas),
                actoTres(compas), actoCuatro(compas));
    }

    /** Acto 1: la telarania aparece y la mascota baja colgada del hilo. */
    private ParallelTransition actoUno(Compas compas) {
        Timeline hilos = new Timeline();
        for (int i = 0; i < capaTelaranias.getChildren().size(); i++) {
            javafx.scene.Node hilo = capaTelaranias.getChildren().get(i);
            hilos.getKeyFrames().add(new KeyFrame(
                    compas.de(60L * i + 100), new KeyValue(hilo.opacityProperty(), 1)));
        }

        Timeline aparecerFondo = new Timeline(
                new KeyFrame(compas.de(1400),
                        new KeyValue(arania.opacityProperty(), 0.22),
                        new KeyValue(arania.scaleXProperty(), 1.08),
                        new KeyValue(arania.scaleYProperty(), 1.08)));

        Timeline bajarHilo = new Timeline(
                new KeyFrame(compas.de(900),
                        new KeyValue(hiloPrincipal.heightProperty(), 84,
                                Interpolator.EASE_OUT)));

        // El estado se enciende ya en el primer acto. Aparecia en el tercero, pero ahora lleva el
        // progreso real del arranque y ese termina en el primer segundo: si esperara al tercero,
        // lo unico que se llegaria a leer seria "TODO LISTO".
        FadeTransition verEstado = new FadeTransition(compas.de(500), estado);
        verEstado.setToValue(1);

        FadeTransition verMascota = new FadeTransition(compas.de(200), colgante.nodo());
        verMascota.setToValue(1);

        // El rebote va en dos tramos y no con un Interpolator.SPLINE que se pase de la marca:
        // JavaFX exige que los cuatro puntos de control esten entre 0 y 1, asi que un spline no
        // puede sobrepasar el destino. Se baja de mas y se sube un poco, que es el mismo efecto.
        // La mascota queda colgando arriba del todo: debajo va el logo y no deben tocarse.
        double reposo = 8;

        TranslateTransition caer = new TranslateTransition(compas.de(760), colgante.nodo());
        caer.setToY(reposo + 26);
        caer.setInterpolator(Interpolator.EASE_IN);

        TranslateTransition recuperar =
                new TranslateTransition(compas.de(320), colgante.nodo());
        recuperar.setToY(reposo);
        recuperar.setInterpolator(Interpolator.EASE_OUT);

        SequentialTransition bajarMascota = new SequentialTransition(caer, recuperar);

        // Arrancar el sprite va dentro de un KeyFrame y no suelto en este metodo: el guion se
        // CONSTRUYE de una vez y se REPRODUCE despues, asi que cualquier efecto escrito aqui
        // ocurriria al construir, no cuando le toca al acto.
        Timeline animarMascota = alEmpezar(() -> {
            colgante.iniciar();
            balancear();
        });

        return new ParallelTransition(
                hilos, aparecerFondo, bajarHilo, verEstado, verMascota, bajarMascota,
                animarMascota);
    }

    /**
     * El tubo encendiendose: punto, linea, apertura y asentamiento.
     *
     * <p><b>No se anima una linea que crece.</b> Lo que se abre es el hueco entre dos persianas
     * negras que arrancan juntas tapandolo todo y se retiran hacia los bordes. Cada una lleva en su
     * canto interior un filo blanco que se va con ella: ese filo es lo que hace que se lea como un
     * tubo encendiendose y no como dos persianas separandose.</p>
     *
     * <p>Dura {@value #MS_ENCENDIDO} ms fijos, al margen de la cancion.</p>
     */
    private SequentialTransition encendidoDelTubo() {
        // 1. El punto se estira hasta cruzar la pantalla. Frena al llegar: arranca de golpe y se
        //    va deteniendo, como el barrido de un tubo que se engancha.
        Timeline punto = new Timeline(
                new KeyFrame(Duration.millis(MS_PUNTO),
                        new KeyValue(barrido.widthProperty(), anchoVentana, Interpolator.EASE_OUT)));

        // 2. Las persianas se retiran y el filo de cada una toma el relevo del barrido.
        Timeline apertura = new Timeline(
                new KeyFrame(Duration.ONE,
                        new KeyValue(barrido.opacityProperty(), 0),
                        new KeyValue(filoArriba.opacityProperty(), 1),
                        new KeyValue(filoAbajo.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(MS_APERTURA),
                        new KeyValue(hojaArriba.translateYProperty(), -mitadVentana,
                                Interpolator.EASE_BOTH),
                        new KeyValue(hojaAbajo.translateYProperty(), altoVentana - mitadVentana,
                                Interpolator.EASE_BOTH)));

        // 3. El fogonazo hace de subida de brillo: es el mismo rectangulo blanco a pantalla
        //    completa que usa el golpe del logo, aqui a una fraccion de su intensidad.
        Timeline asentamiento = new Timeline(
                new KeyFrame(Duration.millis(MS_ASENTAMIENTO * 0.4),
                        new KeyValue(fogonazo.opacityProperty(), BRILLO_ASENTAMIENTO)),
                new KeyFrame(Duration.millis(MS_ASENTAMIENTO),
                        new KeyValue(fogonazo.opacityProperty(), 0),
                        new KeyValue(filoArriba.opacityProperty(), 0),
                        new KeyValue(filoAbajo.opacityProperty(), 0)));

        return new SequentialTransition(punto, apertura, asentamiento);
    }

    /** Una persiana: el panel negro y, en su canto interior, el filo blanco que se va con el. */
    private Pane persiana(boolean esLaDeArriba) {
        // Las dos alturas se reparten el alto exacto sin solaparse ni dejar hueco.
        double altoPanel = esLaDeArriba ? mitadVentana : altoVentana - mitadVentana;
        Rectangle panel = new Rectangle(anchoVentana, altoPanel);
        panel.setFill(FONDO);
        panel.setY(0);

        Rectangle filo = esLaDeArriba ? filoArriba : filoAbajo;
        filo.setWidth(anchoVentana);
        filo.setHeight(GROSOR_BARRIDO);
        filo.setFill(Color.WHITE);
        filo.setOpacity(0);
        // El filo va pegado al canto que mira al centro de la pantalla.
        filo.setY(esLaDeArriba ? altoPanel - GROSOR_BARRIDO : 0);

        Pane hoja = new Pane(panel, filo);
        hoja.setMouseTransparent(true);
        hoja.setLayoutY(esLaDeArriba ? 0 : mitadVentana);
        return hoja;
    }

    /**
     * Entre el acto 1 y el 2: la senial se desgarra en bandas y se vuelve a componer.
     *
     * <p><b>Como esta hecho.</b> Se congela el contenido en una foto y esa unica foto se reparte
     * entre {@value #BANDAS_GLITCH} vistas apiladas, cada una recortando su franja con un
     * {@code viewport}. Correr una banda es mover su {@code translateX}. No se usan recortes sobre
     * el contenido vivo: eso obligaria a recalcular la escena entera treinta veces por segundo en
     * mitad de un arranque que ya va justo.</p>
     *
     * <p>Se cuenta como una maquina acomodandose, no como una falla: el temblor decae hasta cero y
     * la imagen queda quieta antes de que entre el logo.</p>
     */
    private SequentialTransition desgarroDeSenial(Compas compas) {
        // Entre las dos rafagas la imagen vuelve a verse limpia: es lo que hace que se lea como un
        // titileo y no como una sola averia larga.
        return new SequentialTransition(
                rafaga(compas, 0),
                new PauseTransition(compas.de(BASE_GLITCH_PAUSA)),
                rafaga(compas, 1000));
    }

    /**
     * Una sacudida suelta: romper, temblar unos cuadros y volver a componer.
     *
     * @param desdeCuadro desde que cuadro cuenta el ruido. Las dos rafagas arrancan en numeros
     *                    distintos para que no salga dos veces el mismo desgarro; con el mismo
     *                    valor se veria un bucle en vez de dos chispazos
     */
    private SequentialTransition rafaga(Compas compas, int desdeCuadro) {
        Duration total = compas.de(BASE_GLITCH_RAFAGA);
        int cuadros = Math.max(2, (int) Math.round(total.toMillis() / MS_POR_CUADRO));

        Timeline motor = new Timeline();
        motor.getKeyFrames().add(new KeyFrame(Duration.ONE, evento -> romperSenial()));
        for (int i = 1; i <= cuadros; i++) {
            int cuadro = desdeCuadro + i;
            double avance = (double) i / cuadros;
            motor.getKeyFrames().add(new KeyFrame(
                    total.multiply((double) i / cuadros),
                    evento -> correrBandas(cuadro, avance)));
        }

        // La reparacion va en su propio tramo: asi ocurre aunque el motor acabe antes de tiempo.
        return new SequentialTransition(motor, alEmpezar(this::repararSenial));
    }

    /** Congela el contenido en una foto y lo sustituye por las bandas. */
    private void romperSenial() {
        SnapshotParameters ajustes = new SnapshotParameters();
        ajustes.setFill(FONDO);
        // Sin este recorte la foto no mide 900x640 sino 3361x1120: snapshot() abarca todo lo que
        // ocupan los hijos, y los hilos de telarania van rotados 22 grados y se salen del area por
        // los cuatro costados. Las bandas acababan recortando una esquina vacia y solo se veia
        // negro. Con el viewport se fotografia exactamente el rectangulo visible.
        ajustes.setViewport(new Rectangle2D(0, 0, ancho, alto));
        WritableImage foto = contenido.snapshot(ajustes, null);

        double altoBanda = alto / BANDAS_GLITCH;
        for (int i = 0; i < BANDAS_GLITCH; i++) {
            ImageView banda = new ImageView(foto);
            banda.setSmooth(false);
            // El viewport recorta la franja que le toca a esta banda de la foto completa.
            banda.setViewport(new Rectangle2D(0, i * altoBanda, ancho, altoBanda));
            banda.setLayoutY(i * altoBanda);
            capaGlitch.getChildren().add(banda);
        }
        capaGlitch.setVisible(true);
        contenido.setVisible(false);
    }

    /** Corre cada banda lo que le toque en este cuadro. */
    private void correrBandas(int cuadro, double avance) {
        for (int i = 0; i < capaGlitch.getChildren().size(); i++) {
            capaGlitch.getChildren().get(i)
                    .setTranslateX(desplazamientoDeBanda(i, cuadro, avance, ancho));
        }
    }

    /**
     * Devuelve el contenido vivo y suelta las bandas.
     *
     * <p>Se vacia la capa aqui y no al cerrar la ventana: son dos docenas de vistas con una imagen
     * cada una, y no tienen por que seguir en memoria durante el resto de la presentacion. Es
     * idempotente porque tambien se llama al cerrar, por si se salto la intro en mitad del
     * desgarro.</p>
     */
    private void repararSenial() {
        capaGlitch.getChildren().clear();
        capaGlitch.setVisible(false);
        contenido.setVisible(true);
    }

    /** Acto 2: el logo cae encima con un fogonazo y una sacudida. */
    private SequentialTransition actoDos(Compas compas) {
        PauseTransition respiro = new PauseTransition(compas.de(150));

        ScaleTransition impacto = new ScaleTransition(compas.de(420), logo);
        impacto.setToX(1);
        impacto.setToY(1);
        impacto.setInterpolator(Interpolator.EASE_IN);

        FadeTransition verLogo = new FadeTransition(compas.de(300), logo);
        verLogo.setToValue(1);

        ParallelTransition caida = new ParallelTransition(impacto, verLogo);

        // El fogonazo entra al final de la caida: es el golpe.
        Timeline destello = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(fogonazo.opacityProperty(), 0.85)),
                new KeyFrame(compas.de(320), new KeyValue(fogonazo.opacityProperty(), 0)));

        // Se sacude el contenido, no la raiz: la raiz sostiene el marco y el recorte.
        Timeline sacudida = new Timeline(
                new KeyFrame(compas.de(50), new KeyValue(contenido.translateYProperty(), 9)),
                new KeyFrame(compas.de(110), new KeyValue(contenido.translateYProperty(), -6)),
                new KeyFrame(compas.de(170), new KeyValue(contenido.translateXProperty(), 5)),
                new KeyFrame(compas.de(240), new KeyValue(contenido.translateXProperty(), 0),
                        new KeyValue(contenido.translateYProperty(), 0)));

        FadeTransition verRotulo = new FadeTransition(compas.de(400), rotulo);
        verRotulo.setToValue(1);

        return new SequentialTransition(respiro, caida,
                new ParallelTransition(destello, sacudida, verRotulo));
    }

    /**
     * Acto 3: se llena la barra mientras la mascota sigue colgando arriba.
     *
     * <p>El rotulo de estado <b>no</b> se escribe desde aqui. Antes habia un Timeline que soltaba
     * tres frases en tiempos fijos, y como {@link #informar(String)} escribe en ese mismo Label,
     * los dos se pisaban y ganaba el ultimo en llegar. Manda el arranque real: lo que se lee es la
     * etapa en la que va de verdad.</p>
     */
    private ParallelTransition actoTres(Compas compas) {
        Timeline verBarra = new Timeline(
                new KeyFrame(compas.de(300),
                        new KeyValue(barraFondo.opacityProperty(), 1),
                        new KeyValue(barraRelleno.opacityProperty(),
                                BARRA_POR_BLOQUES ? 0 : 1),
                        new KeyValue(bloques.opacityProperty(),
                                BARRA_POR_BLOQUES ? 1 : 0)));

        Timeline llenar = new Timeline(
                new KeyFrame(compas.de(2600),
                        new KeyValue(barraRelleno.widthProperty(), barraFondo.getWidth() - 6)));
        // El relleno crece desde la izquierda; StackPane lo centraria si no se corrige la x.
        barraRelleno.widthProperty().addListener((observable, anterior, actual) ->
                barraRelleno.setTranslateX((actual.doubleValue() - barraFondo.getWidth()) / 2 + 3));

        return new ParallelTransition(verBarra, llenar);
    }

    /** Acto 4: se apaga todo y la musica se va con el. */
    private ParallelTransition actoCuatro(Compas compas) {
        Duration apagado = compas.de(700);
        Timeline apagar = new Timeline(
                new KeyFrame(apagado, new KeyValue(velo.opacityProperty(), 1)));
        // El desvanecido del audio no puede escribirse suelto en este metodo: se disparaba al
        // CONSTRUIR el guion y la cancion se iba a cero en el primer instante de la intro.
        return new ParallelTransition(alEmpezar(() -> musica.desvanecer(apagado)), apagar);
    }

    // --- Cierre ---

    /** Deja que el llamador se entere de cuando la presentacion acabo. */
    public void alTerminar(Runnable accion) {
        this.alTerminar = accion == null ? () -> { } : accion;
        if (terminada) {
            this.alTerminar.run();
        }
    }

    /** Cambia el rotulo de estado; lo usa el arranque para contar por donde va. */
    public void informar(String texto) {
        estado.setText(texto.toUpperCase(java.util.Locale.ROOT));
    }

    /** Corta la presentacion y entra directo a la aplicacion. */
    public void saltar() {
        if (guion != null) {
            guion.stop();
        }
        cerrar();
    }

    /**
     * @return cuanto dura la presentacion; la marca la pista, no una constante
     *
     * <p>Antes de arrancar todavia no se sabe —hay que esperar a que el Media cargue—, y entonces
     * se devuelve el ritmo base, que es lo que se usaria si no hubiera audio.</p>
     */
    public Duration duracion() {
        return compas == null ? Duration.millis(BASE_TOTAL) : compas.total();
    }

    private void cerrar() {
        if (terminada) {
            return;
        }
        terminada = true;
        repararSenial();
        colgante.detener();
        if (vaiven != null) {
            vaiven.stop();
        }
        musica.detener();
        escenario.close();
        alTerminar.run();
    }

    private void centrar() {
        Rectangle2D util = Screen.getPrimary().getVisualBounds();
        escenario.setX(util.getMinX() + (util.getWidth() - escenario.getWidth()) / 2);
        escenario.setY(util.getMinY() + (util.getHeight() - escenario.getHeight()) / 2);
    }

    private static java.util.Optional<Image> cargar(String ruta) {
        try (InputStream flujo = IntroDeArranque.class.getResourceAsStream(ruta)) {
            return flujo == null
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(new Image(flujo));
        } catch (Exception noSePudoLeer) {
            return java.util.Optional.empty();
        }
    }

    /**
     * Cuanto se corre una banda en un cuadro dado del desgarro.
     *
     * <p>Es una funcion pura y determinista: con la misma banda, el mismo cuadro y el mismo avance
     * devuelve siempre lo mismo. No usa {@code Math.random()} —el desgarro tiene que salir igual en
     * cada arranque, si no seria imposible saber si un cambio lo mejoro— y al no depender de JavaFX
     * se puede comprobar en un test normal.</p>
     *
     * <p>Tres propiedades que el efecto necesita y que se resuelven aqui:</p>
     * <ul>
     *   <li><b>Se calma.</b> El desplazamiento se multiplica por {@code (1 - avance)²}, asi que en
     *       {@code avance = 1} vale exactamente cero: la imagen queda quieta, no se corta de golpe.</li>
     *   <li><b>No se mueven todas.</b> Segun el ruido, cerca de un tercio de las bandas devuelve 0
     *       en cada cuadro.</li>
     *   <li><b>No se sale nadie.</b> El tope es {@link #AMPLITUD_GLITCH} por el ancho.</li>
     * </ul>
     *
     * @param banda  indice de la banda, de arriba abajo
     * @param cuadro numero de cuadro dentro del efecto
     * @param avance cuanto ha corrido el efecto, de 0 a 1
     * @param ancho  ancho del area de animacion
     * @return los pixeles que hay que correr esa banda; positivo a la derecha
     */
    static double desplazamientoDeBanda(int banda, int cuadro, double avance, double ancho) {
        if (avance >= 1) {
            return 0;
        }
        int ruido = revolver(banda, cuadro);
        if ((ruido & 0xFF) < UMBRAL_QUIETO) {
            return 0;
        }
        double signo = ((ruido >>> 8) & 1) == 0 ? -1 : 1;
        double magnitud = ((ruido >>> 9) & 0xFF) / 255.0;
        // Al cubo y no al cuadrado: con el cuadrado, a mitad de efecto todavia quedaba la cuarta
        // parte del temblor y la imagen tardaba en asentarse. Al cubo cae mucho antes.
        double calma = (1 - avance) * (1 - avance) * (1 - avance);
        return signo * magnitud * AMPLITUD_GLITCH * ancho * calma;
    }

    /**
     * Mezcla banda y cuadro en un entero bien repartido.
     *
     * <p>Es un hash entero al estilo de los que usan los ruidos de procedimiento: dos numeros
     * primos grandes y unos cuantos desplazamientos. Hace de generador aleatorio con semilla fija,
     * pero sin guardar estado, que es lo que permite preguntar por cualquier cuadro suelto.</p>
     */
    private static int revolver(int banda, int cuadro) {
        int valor = banda * 73856093 ^ cuadro * 19349663;
        valor ^= valor >>> 13;
        valor *= 1274126177;
        valor ^= valor >>> 16;
        return valor & 0x7FFFFFFF;
    }

    /**
     * Envuelve una accion para que ocurra al empezar el acto, no al construirlo.
     *
     * <p><b>El KeyFrame va en {@link Duration#ONE} y no en {@code Duration.ZERO}.</b> Comprobado:
     * un Timeline de duracion cero <i>no llega a disparar</i> su accion, asi que ponerla en el
     * instante cero equivale a no ponerla. Costo tres efectos silenciosos —los dos sprites se
     * quedaban congelados y la musica no se desvanecia nunca— porque el codigo parecia correcto y
     * compilaba igual. Un milisegundo es imperceptible y sí se ejecuta.</p>
     *
     * @param accion lo que hay que hacer al arrancar el acto
     * @return una transicion de un milisegundo que dispara la accion
     */
    private static Timeline alEmpezar(Runnable accion) {
        return new Timeline(new KeyFrame(Duration.ONE, evento -> accion.run()));
    }

    /**
     * Los momentos de la presentacion que vale la pena mirar por separado.
     *
     * <p>Cada uno apunta al <b>centro</b> de su movimiento, no a su borde: en el borde se ve la
     * transicion entre dos cosas y no se entiende ninguna. Por eso ninguno cae en 0 ni en 1.</p>
     *
     * <p>Sirve para dos cosas concretas. Una, revisar un movimiento suelto con
     * {@link IntroDeArranque#irA(Movimiento)} sin esperar a los otros cuatro. Otra, sacar una
     * captura de cada momento sin tener que cazarla al vuelo con un cronometro, que es como se
     * venia haciendo y por eso las capturas caian siempre un poco antes o un poco despues.</p>
     *
     * <p>Los instantes salen del ritmo base, no escritos a mano: si manana un acto cambia de
     * duracion, estos se recolocan solos.</p>
     */
    public enum Movimiento {
        BAJADA("Bajada de la mascota",
                BASE_ACTO_UNO / 2 / BASE_TOTAL),
        DESGARRO("Desgarro de la señal",
                (BASE_ACTO_UNO + BASE_GLITCH / 2) / BASE_TOTAL),
        IMPACTO("Impacto del logo",
                (BASE_ACTO_UNO + BASE_GLITCH + BASE_ACTO_DOS / 2) / BASE_TOTAL),
        CARGA("Barra llenándose",
                (BASE_ACTO_UNO + BASE_GLITCH + BASE_ACTO_DOS + BASE_ACTO_TRES / 2) / BASE_TOTAL),
        FUNDIDO("Fundido final",
                (BASE_TOTAL - BASE_ACTO_CUATRO / 2) / BASE_TOTAL);

        private final String etiqueta;
        private final double instante;

        Movimiento(String etiqueta, double instante) {
            this.etiqueta = etiqueta;
            this.instante = instante;
        }

        /** @return como se llama este momento, para rotularlo en una captura */
        public String etiqueta() {
            return etiqueta;
        }

        /** @return en que punto de la presentacion ocurre, de 0 a 1 */
        public double instante() {
            return instante;
        }
    }

    /**
     * Coloca la presentacion en un momento concreto para poder mirarlo.
     *
     * @param movimiento  el momento que se quiere revisar
     * @param cuandoLlegue que hacer una vez congelada la escena ahi
     */
    public void irA(Movimiento movimiento, Runnable cuandoLlegue) {
        irA(movimiento.instante(), cuandoLlegue);
    }

    /**
     * Coloca la presentacion en un instante dado, de 0 a 1, y la congela ahi.
     *
     * <p><b>Por que se reproduce a toda velocidad en vez de saltar.</b> Saltar directamente no
     * sirve: {@code jumpTo} sobre una animacion parada ni siquiera aplica los valores, y
     * {@code playFrom} solo aplica los del tramo en el que cae. Los tramos anteriores no se
     * ejecutan, asi que al saltar al acto 3 la barra aparecia pero la mascota y el logo seguian
     * invisibles, con la opacidad en cero que tenian al empezar.</p>
     *
     * <p>La alternativa era repetir aqui el estado final de cada acto, y eso es una copia que se
     * desincroniza en cuanto alguien retoca un acto. Reproducir de verdad —solo que
     * {@value #VELOCIDAD_REVISION} veces mas rapido— deja la escena exacta sin duplicar nada, y
     * ademas dispara las acciones por el camino. Tarda una fraccion de segundo.</p>
     *
     * <p>Es asincrono: la escena no esta lista al volver de este metodo, sino cuando se llama a
     * {@code cuandoLlegue}.</p>
     *
     * @param instante     punto de la presentacion, de 0 a 1
     * @param cuandoLlegue que hacer una vez congelada la escena ahi
     */
    public void irA(double instante, Runnable cuandoLlegue) {
        double donde = Math.max(0, Math.min(1, instante));
        if (guion == null) {
            compas = new Compas(null);
            guion = construirGuion(compas);
            guion.setOnFinished(evento -> cerrar());
        }
        Duration objetivo = compas.total().multiply(donde);

        guion.currentTimeProperty().addListener(new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Duration> cual,
                                Duration antes, Duration ahora) {
                if (ahora.lessThan(objetivo)) {
                    return;
                }
                guion.currentTimeProperty().removeListener(this);
                guion.pause();
                guion.setRate(1);
                cuandoLlegue.run();
            }
        });
        guion.setRate(VELOCIDAD_REVISION);
        guion.playFromStart();
    }

    /**
     * Reparte la duracion total entre los actos, conservando sus proporciones.
     *
     * <p>Las duraciones no se escriben en milisegundos: se escriben como el ritmo base
     * ({@code BASE_*}) y este compas las convierte multiplicando por un solo factor. Como el factor
     * es el mismo para todos, el peso relativo de cada acto no cambia nunca: si la pista dura el
     * doble, cada acto dura el doble, y el fundido final sigue cayendo justo al terminar.</p>
     */
    static final class Compas {
        private final Duration total;
        private final double factor;

        /**
         * @param duracionPista lo que dura la cancion, o {@code null} si no hay audio
         */
        Compas(Duration duracionPista) {
            double segundos = duracionPista == null
                    || duracionPista.isUnknown()
                    || duracionPista.isIndefinite()
                    || duracionPista.toSeconds() <= 0
                    ? BASE_TOTAL / 1000
                    : duracionPista.toSeconds();

            segundos = Math.max(MIN_SEGUNDOS, Math.min(MAX_SEGUNDOS, segundos));
            this.total = Duration.seconds(segundos);
            // El encendido del tubo NO se estira con la musica: es un gesto mecanico y a otra
            // velocidad deja de parecer un televisor. Se le descuenta al reparto en vez de alargar
            // el total, asi la presentacion sigue acabando cuando toca.
            double paraLosActos = Math.max(1, segundos * 1000 - MS_ENCENDIDO);
            this.factor = paraLosActos / BASE_TOTAL;
        }

        /**
         * @param base tiempo del ritmo base, en milisegundos
         * @return ese mismo tiempo llevado a la escala de esta ejecucion
         */
        Duration de(double base) {
            return Duration.millis(base * factor);
        }

        Duration total() {
            return total;
        }
    }

    /**
     * Marco de nueve piezas dibujado con los sprites, en vez de un borde de CSS.
     *
     * <p><b>Esquinas fijas, lados repetidos.</b> Los cuatro lados se <i>repiten</i> como azulejos,
     * no se estiran: estirar un pixel art lo emborrona, y ademas el dibujo de cada lado es
     * uniforme a lo largo de su eje, asi que repetirlo no deja costura. Es la misma tecnica que
     * usa el marco de la ventana principal, alli resuelta en CSS.</p>
     *
     * <p>Las ocho piezas existen como archivo, incluidas las cuatro que parecian faltar. No hace
     * falta espejar ninguna —y no habria salido bien: el borde superior mide 79 px de alto y el
     * inferior 42, asi que reflejar el de abajo no habria casado con las esquinas.</p>
     */
    private static final class MarcoPixel {
        private static final String CARPETA = "/imagenes/spidey/marco-intro-";

        private final GridPane rejilla = new GridPane();

        MarcoPixel() {
            rejilla.getColumnConstraints().addAll(
                    columna(MARCO_LADO), columnaElastica(), columna(MARCO_LADO));
            rejilla.getRowConstraints().addAll(
                    fila(MARCO_ARRIBA), filaElastica(), fila(MARCO_ABAJO));

            rejilla.add(pieza("sup-izq", false, false), 0, 0);
            rejilla.add(pieza("sup", true, false), 1, 0);
            rejilla.add(pieza("sup-der", false, false), 2, 0);
            rejilla.add(pieza("izq", false, true), 0, 1);
            rejilla.add(pieza("der", false, true), 2, 1);
            rejilla.add(pieza("inf-izq", false, false), 0, 2);
            rejilla.add(pieza("inf", true, false), 1, 2);
            rejilla.add(pieza("inf-der", false, false), 2, 2);

            // El hueco central queda vacio, pero conviene asegurarse de que ni el marco ni la
            // rejilla se coman un clic: la intro se salta pulsando en cualquier parte.
            rejilla.setMouseTransparent(true);
        }

        GridPane nodo() {
            return rejilla;
        }

        /** Una pieza del marco; se repite en el eje que se le indique. */
        private static Region pieza(String nombre, boolean repetirEnX, boolean repetirEnY) {
            Region region = new Region();
            Image imagen = cargar(CARPETA + nombre + ".png").orElse(null);
            if (imagen == null) {
                return region;
            }
            region.setBackground(new Background(new BackgroundImage(
                    imagen,
                    repetirEnX ? BackgroundRepeat.REPEAT : BackgroundRepeat.NO_REPEAT,
                    repetirEnY ? BackgroundRepeat.REPEAT : BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.DEFAULT,
                    // Tamanio natural del sprite: sin reescalar no hay remuestreo ni borrones.
                    BackgroundSize.DEFAULT)));
            return region;
        }

        private static ColumnConstraints columna(double ancho) {
            ColumnConstraints medida = new ColumnConstraints(ancho);
            medida.setMinWidth(ancho);
            medida.setMaxWidth(ancho);
            return medida;
        }

        private static ColumnConstraints columnaElastica() {
            ColumnConstraints medida = new ColumnConstraints();
            medida.setHgrow(Priority.ALWAYS);
            return medida;
        }

        private static RowConstraints fila(double alto) {
            RowConstraints medida = new RowConstraints(alto);
            medida.setMinHeight(alto);
            medida.setMaxHeight(alto);
            return medida;
        }

        private static RowConstraints filaElastica() {
            RowConstraints medida = new RowConstraints();
            medida.setVgrow(Priority.ALWAYS);
            return medida;
        }
    }

    /**
     * El tema de fondo, aislado para que la presentacion funcione aunque no haya audio.
     *
     * <p>Se instancia dentro de un try: si el archivo no esta en el classpath o la maquina no
     * tiene con que descodificar un MP3, la presentacion corre igual en silencio en vez de tumbar
     * el arranque de la aplicacion.</p>
     */
    private static final class Musica {
        private javafx.scene.media.MediaPlayer reproductor;

        Musica(String ruta) {
            try {
                URL url = IntroDeArranque.class.getResource(ruta);
                if (url == null) {
                    return;
                }
                reproductor = new javafx.scene.media.MediaPlayer(
                        new javafx.scene.media.Media(url.toExternalForm()));
                reproductor.setVolume(0.55);
            } catch (Exception sinAudio) {
                reproductor = null;
            }
        }

        void arrancar() {
            if (reproductor != null) {
                reproductor.play();
            }
        }

        /**
         * Averigua cuanto dura la pista y lo entrega cuando se sepa.
         *
         * <p>{@code Media.getDuration()} solo tiene valor una vez que el archivo termino de
         * cargar, asi que no se puede preguntar y ya. Se resuelve sin bloquear el hilo grafico: si
         * ya esta listo se contesta en el acto, y si no, se atiende {@code setOnReady} y ademas se
         * arma un plazo por si nunca llega —un archivo corrupto dejaria la aplicacion esperando
         * para siempre en una pantalla negra—.</p>
         *
         * @param queHacer recibe la duracion, o {@code null} si no hay audio o no se pudo saber
         */
        void cuandoSepaLaDuracion(java.util.function.Consumer<Duration> queHacer) {
            if (reproductor == null) {
                queHacer.accept(null);
                return;
            }
            // Una sola respuesta: gana quien llegue antes, el Media o el plazo.
            boolean[] yaRespondio = {false};
            java.util.function.Consumer<Duration> responder = duracion -> {
                if (!yaRespondio[0]) {
                    yaRespondio[0] = true;
                    queHacer.accept(duracion);
                }
            };

            if (reproductor.getStatus() == javafx.scene.media.MediaPlayer.Status.READY) {
                responder.accept(reproductor.getMedia().getDuration());
                return;
            }
            reproductor.setOnReady(() -> responder.accept(reproductor.getMedia().getDuration()));
            reproductor.setOnError(() -> responder.accept(null));

            PauseTransition plazo = new PauseTransition(ESPERA_MEDIA);
            plazo.setOnFinished(evento -> responder.accept(null));
            plazo.play();
        }

        /** Baja el volumen poco a poco en vez de cortar de golpe. */
        void desvanecer(Duration cuanto) {
            if (reproductor == null) {
                return;
            }
            new Timeline(new KeyFrame(cuanto,
                    new KeyValue(reproductor.volumeProperty(), 0))).play();
        }

        void detener() {
            if (reproductor != null) {
                reproductor.stop();
                reproductor.dispose();
            }
        }
    }
}
