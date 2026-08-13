package com.eia.reproductor;

import javafx.application.Application;

/**
 * Punto de arranque de la aplicacion. <b>Esta es la clase que hay que ejecutar.</b>
 *
 * <p><b>Por que existe esta clase y no se arranca directamente {@link App}.</b></p>
 *
 * <p>Cuando el lanzador de la JVM recibe como clase principal una que extiende
 * {@link Application}, no la arranca de forma normal: la deriva a un camino especial que exige que
 * el modulo {@code javafx.graphics} este cargado como <i>modulo con nombre</i>, es decir en el
 * <i>module path</i>. Como este proyecto es deliberadamente no modular (no hay
 * {@code module-info.java}, porque Gson y jaudiotagger usan reflexion y bajo JPMS obligarian a
 * declarar {@code opens} fragiles), las librerias de JavaFX viajan en el <i>classpath</i>. El
 * resultado es el error:</p>
 *
 * <pre>Error: JavaFX runtime components are missing, and are required to run this application</pre>
 *
 * <p>Esa verificacion solo se dispara si la clase principal <b>es</b> una subclase de
 * {@code Application}. {@code Lanzador} no lo es: es una clase corriente cuyo {@code main} llama a
 * {@link Application#launch(Class, String...)}. Asi la JVM la arranca sin el chequeo y JavaFX se
 * carga sin problema desde el classpath.</p>
 *
 * <p>La consecuencia practica es que el proyecto se ejecuta con la flecha verde de cualquier IDE,
 * sin configurar <i>VM options</i> ni rutas absolutas en la maquina de cada integrante del grupo.</p>
 *
 * @see App
 */
public final class Lanzador {

    /** Clase de utilidad: no tiene sentido instanciarla. */
    private Lanzador() {
    }

    /**
     * Arranca la interfaz grafica.
     *
     * @param argumentos argumentos de linea de comandos, no se utilizan
     */
    public static void main(String[] argumentos) {
        Application.launch(App.class, argumentos);
    }
}
