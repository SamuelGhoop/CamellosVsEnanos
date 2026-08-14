package com.eia.reproductor.controlador;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;

/**
 * Convierte una ventana cualquiera en una ventana con la estetica del reproductor.
 *
 * <p><b>Que arregla.</b> Los dialogos salian con la barra de titulo blanca de Windows encima del
 * marco pixel, y el contraste dejaba en evidencia que la estetica era solo una capa de pintura.
 * Aqui se quita esa barra y se dibuja una propia, igual que en la ventana principal.</p>
 *
 * <p>Se aplica desde codigo y no desde cada FXML para no repetir la misma barra en cada vista: la
 * ventana que la necesite llama a {@link #montar}, y si manana cambia el estilo, cambia en un solo
 * sitio.</p>
 */
public final class VentanaPixel {

    private static final String RUTA_ESTILOS = "/vista/estilos.css";

    private VentanaPixel() {
    }

    /**
     * Monta el contenido dentro de un marco pixel con barra de titulo propia.
     *
     * @param escenario ventana a vestir; se le quita la decoracion del sistema
     * @param titulo    texto de la barra
     * @param contenido vista ya cargada
     * @return la escena creada, por si hay que retocarla
     */
    public static Scene montar(Stage escenario, String titulo, Parent contenido) {
        escenario.initStyle(StageStyle.TRANSPARENT);

        VBox marco = new VBox(barraDeTitulo(escenario, titulo), contenido);
        marco.getStyleClass().add("marco-ventana");
        VBox.setVgrow(contenido, Priority.ALWAYS);

        Scene escena = new Scene(marco);
        // Sin esto, las esquinas del marco salen con el gris por defecto de la escena.
        escena.setFill(Color.TRANSPARENT);

        URL hoja = VentanaPixel.class.getResource(RUTA_ESTILOS);
        if (hoja != null) {
            escena.getStylesheets().add(hoja.toExternalForm());
        }
        escenario.setScene(escena);
        return escena;
    }

    /**
     * Arma la barra superior: titulo a la izquierda y boton de cerrar a la derecha.
     *
     * <p>Tambien es el asa para mover la ventana, porque al quitar la decoracion del sistema se
     * pierde la unica zona por la que se podia arrastrar.</p>
     */
    private static HBox barraDeTitulo(Stage escenario, String titulo) {
        Label texto = new Label(titulo.toUpperCase(java.util.Locale.ROOT));
        texto.getStyleClass().add("barra-titulo-texto");

        Region empuje = new Region();
        HBox.setHgrow(empuje, Priority.ALWAYS);

        Button cerrar = new Button("X");
        cerrar.getStyleClass().add("boton-ventana");
        cerrar.setOnAction(evento -> escenario.close());

        HBox barra = new HBox(texto, empuje, cerrar);
        barra.getStyleClass().add("barra-titulo");
        hacerArrastrable(barra, escenario);
        return barra;
    }

    /** Guarda donde se agarro la ventana para que no salte al empezar a moverla. */
    private static void hacerArrastrable(HBox barra, Stage escenario) {
        final double[] agarre = new double[2];
        barra.setOnMousePressed(evento -> {
            agarre[0] = evento.getSceneX();
            agarre[1] = evento.getSceneY();
        });
        barra.setOnMouseDragged(evento -> {
            escenario.setX(evento.getScreenX() - agarre[0]);
            escenario.setY(evento.getScreenY() - agarre[1]);
        });
    }
}
