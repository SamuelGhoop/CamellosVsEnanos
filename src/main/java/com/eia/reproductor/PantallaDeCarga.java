package com.eia.reproductor;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;

/**
 * Ventana de bienvenida que se ve mientras la aplicacion termina de arrancar.
 *
 * <p><b>Por que hace falta.</b> Entre que se lanza el proceso y aparece la ventana principal pasan
 * varios segundos: se registra la tipografia, se carga el FXML, se lee la biblioteca del disco y se
 * levanta el audio. Sin nada en pantalla, parece que no se abrio y la gente vuelve a hacer doble
 * clic.</p>
 *
 * <p>Es deliberadamente ligera —una imagen y una etiqueta, sin FXML ni hoja de estilos— porque
 * cargar cualquiera de esas dos cosas la retrasaria justo lo que intenta disimular.</p>
 */
public final class PantallaDeCarga {

    /** Ancho de la ventanita; el alto lo pone el contenido. */
    private static final double ANCHO = 340;

    /** Escala del logo. El original mide 192x187, que se queda corto para una portada. */
    private static final double ESCALA_LOGO = 1.4;

    /** Cada cuanto avanzan los puntos suspensivos del texto. */
    private static final Duration RITMO_PUNTOS = Duration.millis(400);

    private static final int MAXIMO_PUNTOS = 3;

    private final Stage escenario = new Stage(StageStyle.TRANSPARENT);
    private final Label mensaje = new Label("CARGANDO");
    private Timeline puntos;

    /** Crea la ventana y la deja lista para mostrarse. */
    public PantallaDeCarga() {
        ImageView logo = new ImageView();
        cargarLogo().ifPresent(logo::setImage);
        logo.setPreserveRatio(true);
        logo.setFitWidth(logo.getImage() == null ? 0 : logo.getImage().getWidth() * ESCALA_LOGO);
        // Pixel art: sin suavizado, o el escalado lo emborrona.
        logo.setSmooth(false);

        mensaje.setStyle("-fx-font-family: 'Press Start 2P'; -fx-font-size: 10px;"
                + " -fx-text-fill: #ABE5FF;");

        Label titulo = new Label("CAMELLOS VS ENANOS");
        titulo.setStyle("-fx-font-family: 'Press Start 2P'; -fx-font-size: 12px;"
                + " -fx-text-fill: #d3323f;");

        VBox contenido = new VBox(18, logo, titulo, mensaje);
        contenido.setAlignment(Pos.CENTER);
        // El marco de doble bisel y el fondo negro son los mismos del reproductor, para que la
        // pantalla de carga no parezca de otro programa.
        contenido.setStyle("-fx-background-color: #0B0B07;"
                + " -fx-border-width: 4;"
                + " -fx-border-color: #ABE5FF #154F80 #154F80 #ABE5FF;"
                + " -fx-padding: 28;");
        contenido.setPrefWidth(ANCHO);

        Scene escena = new Scene(contenido);
        escena.setFill(Color.TRANSPARENT);
        escenario.setScene(escena);
        escenario.setAlwaysOnTop(true);
    }

    /** Muestra la ventana centrada y arranca la animacion de los puntos. */
    public void mostrar() {
        escenario.show();

        Rectangle2D util = Screen.getPrimary().getVisualBounds();
        escenario.setX(util.getMinX() + (util.getWidth() - escenario.getWidth()) / 2);
        escenario.setY(util.getMinY() + (util.getHeight() - escenario.getHeight()) / 2);

        puntos = new Timeline(new KeyFrame(RITMO_PUNTOS, evento -> avanzarPuntos()));
        puntos.setCycleCount(Timeline.INDEFINITE);
        puntos.play();
    }

    /**
     * Cambia el texto de estado, para que se vea que el arranque avanza.
     *
     * @param texto que se esta haciendo ahora mismo
     */
    public void informar(String texto) {
        mensaje.setText(texto.toUpperCase(java.util.Locale.ROOT));
    }

    /** Cierra la ventana y detiene la animacion. */
    public void cerrar() {
        if (puntos != null) {
            puntos.stop();
        }
        escenario.close();
    }

    private void avanzarPuntos() {
        String texto = mensaje.getText();
        String base = texto.replace(".", "");
        int cuantos = texto.length() - base.length();
        mensaje.setText(base + ".".repeat((cuantos + 1) % (MAXIMO_PUNTOS + 1)));
    }

    private static java.util.Optional<Image> cargarLogo() {
        try (InputStream flujo = PantallaDeCarga.class.getResourceAsStream(App.RUTA_LOGO)) {
            return flujo == null
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(new Image(flujo));
        } catch (IOException noSePudoLeer) {
            return java.util.Optional.empty();
        }
    }
}
