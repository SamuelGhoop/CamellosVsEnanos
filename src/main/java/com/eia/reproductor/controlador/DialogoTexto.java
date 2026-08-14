package com.eia.reproductor.controlador;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Ventanita para pedir un texto corto, con la estetica del reproductor.
 *
 * <p>Sustituye al {@code TextInputDialog} de JavaFX, que llega con la barra de titulo del sistema y
 * botones redondeados: en medio del pixel art se veia como una ventana de otro programa. Reescribir
 * algo tan pequeño sale mas barato que pelearse con el CSS interno de un control que no fue pensado
 * para cambiar tanto de aspecto.</p>
 */
public final class DialogoTexto {

    private static final double ANCHO = 460;

    private DialogoTexto() {
    }

    /**
     * Pide un texto al usuario y espera a que responda.
     *
     * @param duenio   ventana sobre la que se abre
     * @param titulo   texto de la barra superior
     * @param pregunta que se le pide
     * @param inicial  valor con el que arranca el campo
     * @return lo escrito ya recortado, o vacio si cancelo o lo dejo en blanco
     */
    public static Optional<String> pedir(Window duenio, String titulo, String pregunta,
                                         String inicial) {
        Stage escenario = new Stage();
        escenario.initModality(Modality.WINDOW_MODAL);
        if (duenio != null) {
            escenario.initOwner(duenio);
        }

        Label etiqueta = new Label(pregunta.toUpperCase(java.util.Locale.ROOT));
        etiqueta.getStyleClass().add("etiqueta-campo");
        etiqueta.setWrapText(true);

        TextField campo = new TextField(inicial == null ? "" : inicial);
        campo.setPrefWidth(ANCHO - 60);

        // Se devuelve por referencia porque los manejadores no pueden asignar a una variable local.
        final String[] respuesta = new String[1];

        Button aceptar = new Button("ACEPTAR");
        aceptar.setDefaultButton(true);
        aceptar.setOnAction(evento -> {
            respuesta[0] = campo.getText();
            escenario.close();
        });

        Button cancelar = new Button("CANCELAR");
        cancelar.setCancelButton(true);
        cancelar.setOnAction(evento -> escenario.close());

        // Enter confirma sin tener que ir al boton: es un formulario de un solo campo.
        campo.setOnKeyPressed(evento -> {
            if (evento.getCode() == KeyCode.ENTER) {
                aceptar.fire();
            } else if (evento.getCode() == KeyCode.ESCAPE) {
                cancelar.fire();
            }
        });

        Region empuje = new Region();
        HBox.setHgrow(empuje, Priority.ALWAYS);
        HBox botones = new HBox(10, empuje, cancelar, aceptar);
        botones.setAlignment(Pos.CENTER_RIGHT);

        VBox contenido = new VBox(14, etiqueta, campo, botones);
        contenido.getStyleClass().add("cuerpo-dialogo");
        contenido.setPrefWidth(ANCHO);

        VentanaPixel.montar(escenario, titulo, contenido);
        escenario.setResizable(false);
        // El foco en el campo evita el clic de mas que todo el mundo da al abrirse.
        campo.requestFocus();
        campo.selectAll();
        escenario.showAndWait();

        return Optional.ofNullable(respuesta[0]).map(String::trim).filter(texto -> !texto.isEmpty());
    }
}
