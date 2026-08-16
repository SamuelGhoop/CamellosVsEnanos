package com.eia.reproductor;

import javafx.application.Application;

/** Punto de arranque de la aplicacion. */
public final class Lanzador {
    /** Clase de utilidad: no tiene sentido instanciarla. */
    private Lanzador() {
    }

    /** Arranca la interfaz grafica. */
    public static void main(String[] argumentos) {
        Application.launch(App.class, argumentos);
    }
}
