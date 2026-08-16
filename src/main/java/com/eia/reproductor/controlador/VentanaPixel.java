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
import javafx.stage.Window;

import java.net.URL;

/** Convierte una ventana cualquiera en una ventana con la estetica del reproductor. */
public final class VentanaPixel {
    private static final String RUTA_ESTILOS = "/vista/estilos.css";

    /** La misma clase que usa la ventana principal; la hoja de estilos hace el resto. */
    private static final String CLASE_TEMA_CLARO = "tema-claro";

    private VentanaPixel() {
    }

    /** Monta el contenido dentro de un marco pixel con barra de titulo propia. */
    public static Scene montar(Stage escenario, String titulo, Parent contenido) {
        escenario.initStyle(StageStyle.TRANSPARENT);

        Scene escena = new Scene(marco(escenario, titulo, contenido));
        // Sin esto, las esquinas del marco salen con el gris por defecto de la escena.
        escena.setFill(Color.TRANSPARENT);

        URL hoja = VentanaPixel.class.getResource(RUTA_ESTILOS);
        if (hoja != null) {
            escena.getStylesheets().add(hoja.toExternalForm());
        }
        heredarTema(escenario, escena);
        escenario.setScene(escena);
        return escena;
    }

    /** Copia el tema de la ventana que abre el dialogo. */
    private static void heredarTema(Stage escenario, Scene escena) {
        Window duenio = escenario.getOwner();
        if (duenio == null || duenio.getScene() == null) {
            return;
        }
        if (duenio.getScene().getRoot().getStyleClass().contains(CLASE_TEMA_CLARO)) {
            escena.getRoot().getStyleClass().add(CLASE_TEMA_CLARO);
        }
    }

    /** Pone o quita el tema claro en una ventana ya abierta. */
    public static void aplicarTema(Stage escenario, boolean claro) {
        if (escenario == null || escenario.getScene() == null) {
            return;
        }
        var clases = escenario.getScene().getRoot().getStyleClass();
        if (claro) {
            if (!clases.contains(CLASE_TEMA_CLARO)) {
                clases.add(CLASE_TEMA_CLARO);
            }
        } else {
            clases.remove(CLASE_TEMA_CLARO);
        }
    }

    /** Arma el marco con su barra, sin tocar la escena. */
    public static Parent marco(Stage escenario, String titulo, Parent contenido) {
        VBox marco = new VBox(barraDeTitulo(escenario, titulo), contenido);
        marco.getStyleClass().add("marco-ventana");
        VBox.setVgrow(contenido, Priority.ALWAYS);
        return marco;
    }

    /** Arma la barra superior: titulo a la izquierda y boton de cerrar a la derecha. */
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
