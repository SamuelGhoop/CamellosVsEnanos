package com.eia.reproductor;

import com.eia.reproductor.controlador.PrincipalController;
import com.eia.reproductor.controlador.RedimensionadorVentana;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

/**
 * Aplicacion JavaFX del reproductor.
 *
 * <p>Su unica responsabilidad es levantar la ventana principal: registrar la tipografia de pixeles,
 * cargar la vista declarativa {@code principal.fxml} y aplicarle la hoja de estilos. Toda la logica
 * del reproductor vive en los paquetes {@code estructuras}, {@code modos} y {@code servicios}, que
 * no conocen a JavaFX.</p>
 *
 * <p><b>Esta clase no se ejecuta directamente.</b> No tiene {@code main} a proposito: el arranque
 * se hace desde {@link Lanzador}, que explica en su documentacion por que es necesario.</p>
 *
 * @see Lanzador
 */
public class App extends Application {

    /** Ruta de la vista principal dentro de {@code src/main/resources}. */
    private static final String RUTA_VISTA_PRINCIPAL = "/vista/principal.fxml";

    /** Ruta de la hoja de estilos con la estetica pixel-art. */
    private static final String RUTA_ESTILOS = "/vista/estilos.css";

    /** Tipografia de pixeles licenciada bajo OFL (Google Fonts). */
    private static final String RUTA_FUENTE_PIXEL = "/fonts/PressStart2P-Regular.ttf";

    private static final String TITULO_VENTANA = "CAMELLOS VS ENANOS • REPRODUCTOR";

    private static final double ANCHO_INICIAL = 1240;
    private static final double ALTO_INICIAL = 800;
    private static final double ANCHO_MINIMO = 1020;
    private static final double ALTO_MINIMO = 700;

    /** Tamano con el que se registra la fuente; el tamano real lo define el CSS. */
    private static final double TAMANIO_CARGA_FUENTE = 12;

    /** Evita registrar la fuente mas de una vez. */
    private static boolean fuenteRegistrada;

    @Override
    public void start(Stage escenarioPrincipal) throws IOException {
        // La fuente debe registrarse ANTES de construir la escena: si el CSS pide una familia
        // que todavia no esta cargada, JavaFX cae silenciosamente a la fuente por defecto.
        cargarFuentePixel();

        URL urlVista = Objects.requireNonNull(
                App.class.getResource(RUTA_VISTA_PRINCIPAL),
                "No se encontro la vista " + RUTA_VISTA_PRINCIPAL + " en el classpath.");
        FXMLLoader cargador = new FXMLLoader(urlVista);
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

        // Arranca maximizada: con el marco de 40 px y el panel de reproduccion, la ventana en
        // tamanio reducido deja la biblioteca demasiado estrecha.
        escenarioPrincipal.setMaximized(true);
        escenarioPrincipal.show();
    }

    /**
     * Registra la tipografia de pixeles en el motor de fuentes de JavaFX.
     *
     * <p>Es publica y estatica porque toda ventana que se abra necesita la fuente ya registrada, y
     * no todas pasan por {@link #start(Stage)}: un dialogo que se muestre desde una prueba o desde
     * otro punto de entrada se veria con la tipografia del sistema. Cualquiera puede llamarla sin
     * miedo, porque solo hace el trabajo la primera vez.</p>
     *
     * <p>Si el archivo no esta disponible la aplicacion no se detiene: se avisa por consola y el
     * CSS usa la fuente monoespaciada de respaldo declarada en {@code estilos.css}.</p>
     */
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
