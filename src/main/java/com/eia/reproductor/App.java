package com.eia.reproductor;

import com.eia.reproductor.controlador.PrincipalController;
import com.eia.reproductor.controlador.RedimensionadorVentana;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

/** Aplicacion JavaFX del reproductor. */
public class App extends Application {
    /** Ruta de la vista principal dentro de {@code src/main/resources}. */
    private static final String RUTA_VISTA_PRINCIPAL = "/vista/principal.fxml";

    /** Ruta de la hoja de estilos con la estetica pixel-art. */
    private static final String RUTA_ESTILOS = "/vista/estilos.css";

    /** Tipografia de pixeles licenciada bajo OFL (Google Fonts). */
    private static final String RUTA_FUENTE_PIXEL = "/fonts/PressStart2P-Regular.ttf";

    /** Logo del reproductor: barra de tareas y pantalla de carga. */
    public static final String RUTA_LOGO = "/imagenes/logo-carga.png";

    private static final String TITULO_VENTANA = "CAMELLOS VS ENANOS • REPRODUCTOR";

    private static final double ANCHO_INICIAL = 1240;
    private static final double ALTO_INICIAL = 800;
    private static final double ANCHO_MINIMO = 1020;
    private static final double ALTO_MINIMO = 700;

    /** Aire alrededor de la ventana, para que se vea que no ocupa toda la pantalla. */
    private static final double MARGEN_PANTALLA = 40;

    /** Tamano con el que se registra la fuente; el tamano real lo define el CSS. */
    private static final double TAMANIO_CARGA_FUENTE = 12;

    /** Evita registrar la fuente mas de una vez. */
    private static boolean fuenteRegistrada;

    @Override
    public void start(Stage escenarioPrincipal) {
        // La fuente debe registrarse ANTES de construir la escena: si el CSS pide una familia
        // que todavia no esta cargada, JavaFX cae silenciosamente a la fuente por defecto. Y
        cargarFuentePixel();

        IntroDeArranque intro = new IntroDeArranque();
        intro.mostrar();

        // La ventana principal se muestra cuando la presentacion termina, no antes. Si el usuario
        // la salta con un clic, entra en ese momento: alTerminar dispara enseguida si ya acabo.
        intro.alTerminar(() -> {
            escenarioPrincipal.show();
            // Se coloca DESPUES de mostrarla. Al mostrarse, JavaFX ajusta la ventana al tamanio
            // preferido de la escena —aqui 1086 px de alto— y pisaba cualquier medida puesta antes.
            acomodarEnPantalla(escenarioPrincipal);
        });

        // El montaje bloquea el hilo grafico casi un segundo, asi que se hace pronto: en la primera
        // fase solo hay un fundido lento y el tiron no se nota. Sin este respiro previo, la
        // presentacion saldria en blanco porque no le habria dado tiempo a pintar un fotograma.
        PauseTransition respiro = new PauseTransition(Duration.millis(120));
        respiro.setOnFinished(evento -> {
            try {
                construirVentanaPrincipal(escenarioPrincipal, intro);
            } catch (IOException fallo) {
                intro.saltar();
                throw new IllegalStateException("No se pudo construir la ventana principal.", fallo);
            }
        });
        respiro.play();
    }

    /** Monta la ventana principal y la deja lista; quien la muestra es la presentacion. */
    private void construirVentanaPrincipal(Stage escenarioPrincipal, IntroDeArranque intro)
            throws IOException {
        intro.informar("Cargando la interfaz");

        URL urlVista = Objects.requireNonNull(
                App.class.getResource(RUTA_VISTA_PRINCIPAL),
                "No se encontro la vista " + RUTA_VISTA_PRINCIPAL + " en el classpath.");
        FXMLLoader cargador = new FXMLLoader(urlVista);
        // Aqui dentro el controlador lee la biblioteca y las listas del disco: es la parte lenta.
        intro.informar("Leyendo la biblioteca");
        Parent raiz = cargador.load();
        PrincipalController controlador = cargador.getController();

        Scene escena = new Scene(raiz, ANCHO_INICIAL, ALTO_INICIAL);
        aplicarEstilos(escena);

        // Sin decoracion del sistema: la barra de titulo la dibuja la propia aplicacion, con la
        // estetica pixel del resto. El arrastre y los botones los implementa el controlador.
        escenarioPrincipal.initStyle(StageStyle.UNDECORATED);

        // El enunciado pide guardar tambien al cerrar la aplicacion, no solo tras cada cambio.
        escenarioPrincipal.setOnCloseRequest(evento -> controlador.alCerrar());

        escenarioPrincipal.setTitle(TITULO_VENTANA);
        escenarioPrincipal.setScene(escena);
        escenarioPrincipal.setMinWidth(ANCHO_MINIMO);
        escenarioPrincipal.setMinHeight(ALTO_MINIMO);

        // Sin decoracion tampoco hay bordes que arrastrar, asi que se instalan unos propios.
        RedimensionadorVentana.instalar(escenarioPrincipal, (javafx.scene.layout.Region) raiz);

        ponerIconoDeLaBarraDeTareas(escenarioPrincipal);
        intro.informar("Todo listo");
    }

    /** Deja la ventana con un tamanio comodo y centrada, sin taparlo todo. */
    private static void acomodarEnPantalla(Stage escenario) {
        Rectangle2D util = Screen.getPrimary().getVisualBounds();

        // Se aprovecha casi todo el alto disponible. El panel del reproductor lleva caratula,
        // datos, progreso, transporte y la lista de proximas: con 800 px se desbordaba por abajo y
        double ancho = Math.min(ANCHO_INICIAL, util.getWidth() - MARGEN_PANTALLA * 2);
        double alto = Math.max(ALTO_MINIMO, util.getHeight() - MARGEN_PANTALLA);

        escenario.setWidth(ancho);
        escenario.setHeight(alto);
        escenario.setX(util.getMinX() + (util.getWidth() - ancho) / 2);
        escenario.setY(util.getMinY() + (util.getHeight() - alto) / 2);
    }

    /** Pone el logo en la barra de tareas y en el conmutador de ventanas. */
    private static void ponerIconoDeLaBarraDeTareas(Stage escenario) {
        try (InputStream flujo = App.class.getResourceAsStream(RUTA_LOGO)) {
            if (flujo != null) {
                escenario.getIcons().add(new Image(flujo));
            }
        } catch (IOException noSePudoLeer) {
            // Quedarse con el icono de Java es feo, pero no impide usar la aplicacion.
            System.err.println("No se pudo cargar el icono: " + noSePudoLeer.getMessage());
        }
    }

    /** Registra la tipografia de pixeles en el motor de fuentes de JavaFX. */
    public static synchronized void cargarFuentePixel() {
        if (fuenteRegistrada) {
            return;
        }
        fuenteRegistrada = true;
        try (InputStream flujoFuente = App.class.getResourceAsStream(RUTA_FUENTE_PIXEL)) {
            if (flujoFuente == null) {
                System.err.println("[AVISO] No se encontro la fuente " + RUTA_FUENTE_PIXEL
                        + ". Se usara la fuente monoespaciada de respaldo.");
                return;
            }
            Font fuente = Font.loadFont(flujoFuente, TAMANIO_CARGA_FUENTE);
            if (fuente == null) {
                System.err.println("[AVISO] La fuente " + RUTA_FUENTE_PIXEL + " no se pudo registrar.");
            }
        } catch (IOException excepcion) {
            System.err.println("[AVISO] Error leyendo la fuente de pixeles: " + excepcion.getMessage());
        }
    }

    private void aplicarEstilos(Scene escena) {
        URL urlEstilos = App.class.getResource(RUTA_ESTILOS);
        if (urlEstilos == null) {
            System.err.println("[AVISO] No se encontro la hoja de estilos " + RUTA_ESTILOS + ".");
            return;
        }
        escena.getStylesheets().add(urlEstilos.toExternalForm());
    }
}
