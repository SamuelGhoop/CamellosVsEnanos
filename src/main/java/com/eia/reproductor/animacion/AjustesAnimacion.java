package com.eia.reproductor.animacion;

import javafx.util.Duration;

/**
 * Todos los numeros de las animaciones, juntos en un solo sitio.
 *
 * <p>Estan aqui y no repartidos por las clases para poder retocar el ritmo, los tamanios o los
 * recorridos sin salir de este archivo. Si algo se ve demasiado rapido, demasiado grande o
 * demasiado insistente, se ajusta una constante de abajo y listo.</p>
 *
 * <p>Las medidas de los cuadros no son inventadas: salen de medir las hojas de sprites. Cada una
 * lleva anotado de donde viene, porque si mas adelante se reemplaza un PNG hay que volver a
 * medirlo o la animacion se descuadra.</p>
 */
public final class AjustesAnimacion {

    private AjustesAnimacion() {
    }

    // ==================================================================
    // Escala general
    // ==================================================================

    /**
     * Factor de ampliacion de los sprites. <b>Debe ser entero</b>: con valores fraccionarios el
     * pixel art se descuadra aunque se dibuje sin suavizado.
     */
    public static final int ESCALA = 1;

    // ==================================================================
    // Centinela: el que esta de pie en la esquina
    // Hoja medida: 5624 x 119 = 76 cuadros de 74 x 119
    // ==================================================================

    public static final String RUTA_CENTINELA = "/imagenes/spidey/hoja-centinela.png";
    public static final int CENTINELA_CUADROS = 76;
    public static final int CENTINELA_ANCHO_CUADRO = 74;
    public static final int CENTINELA_ALTO_CUADRO = 119;
    public static final double CENTINELA_FPS = 20;

    /**
     * Fila donde terminan los pies dentro del cuadro (el dibujo llega hasta y=114 y despues hay
     * 4 px de aire). Se usa para apoyarlo sobre la plataforma sin que quede flotando.
     */
    public static final int CENTINELA_FILA_PIES = 115;

    /** Si el centinela se dibuja dentro de su marco octogonal o suelto. */
    public static final boolean CENTINELA_CON_PLATAFORMA = true;

    /**
     * Marco octogonal que hace de fondo del centinela. Medido: 114 x 110.
     *
     * <p>Va <b>detras</b> del personaje, alineado por abajo con la linea de sus pies: el efecto
     * es el de una insignia con el personaje parado dentro, no el de una plataforma debajo. Como
     * el sprite mide 119 y el marco 110, la cabeza asoma 5 px por encima del borde superior, que
     * es justo como se ve en la referencia.</p>
     */
    public static final String RUTA_MARCO = "/imagenes/spidey/contenedor.png";
    public static final int MARCO_ANCHO = 114;
    public static final int MARCO_ALTO = 110;

    /** Separacion desde el borde izquierdo de la ventana. */
    public static final double CENTINELA_MARGEN_IZQUIERDO = 72;

    /** Separacion desde el borde inferior de la ventana. */
    public static final double CENTINELA_MARGEN_INFERIOR = 26;

    /**
     * Ancho que la barra inferior reserva a la izquierda para el centinela.
     *
     * <p>El texto rojo de la marquesina arranca despues de esta franja, asi que nunca le pasa por
     * encima ni por detras.</p>
     */
    public static final double CENTINELA_ESPACIO_RESERVADO = 150;

    // ==================================================================
    // Visitante colgante: el que baja de la telarania cada tanto
    // Hoja medida: 6314 x 124 = 77 cuadros de 82 x 124
    // ==================================================================

    public static final String RUTA_COLGANTE = "/imagenes/spidey/hoja-colgante.png";
    public static final int COLGANTE_CUADROS = 77;
    public static final int COLGANTE_ANCHO_CUADRO = 82;
    public static final int COLGANTE_ALTO_CUADRO = 124;
    public static final double COLGANTE_FPS = 20;

    /**
     * Columna del cuadro donde el sprite ya trae dibujado su trozo de telarania (x=34..46).
     * La linea que dibujamos tiene que caer justo ahi para que empalme sin escalon.
     */
    public static final double COLGANTE_COLUMNA_TELARANA = 40;

    /** Cada cuanto aparece: se sortea un tiempo entre estos dos valores. */
    public static final Duration COLGANTE_ESPERA_MINIMA = Duration.seconds(14);
    public static final Duration COLGANTE_ESPERA_MAXIMA = Duration.seconds(32);

    /** Primera aparicion, para que no salte encima del arranque de la aplicacion. */
    public static final Duration COLGANTE_ESPERA_INICIAL = Duration.seconds(6);

    /** Hasta donde baja: se sortea entre estos dos valores en cada visita. */
    public static final double COLGANTE_DESCENSO_MINIMO = 70;
    public static final double COLGANTE_DESCENSO_MAXIMO = 300;

    /** Franja horizontal por donde puede aparecer, como fraccion del ancho disponible. */
    public static final double COLGANTE_FRANJA_IZQUIERDA = 0.20;
    public static final double COLGANTE_FRANJA_DERECHA = 0.72;

    public static final Duration COLGANTE_BAJADA = Duration.seconds(1.7);
    public static final Duration COLGANTE_PERMANENCIA = Duration.seconds(3.4);
    public static final Duration COLGANTE_SUBIDA = Duration.seconds(1.4);

    /**
     * Franja inferior intocable. El colgante nunca desciende dentro de ella, para no quedar
     * encima de los botones ni de la barra inferior.
     */
    public static final double COLGANTE_ZONA_PROHIBIDA_ABAJO = 150;

    public static final double TELARANA_GROSOR = 2;
    public static final String TELARANA_COLOR = "#f0f0f0";

    // ==================================================================
    // Barras de sonido (ecualizador)
    // Valores tomados del CSS de referencia: 9 barras de 10 px con 3 px de separacion, 15 px de
    // alto, animacion de 0.8 s que va de scaleY(0.2) a scaleY(1) y vuelve.
    // ==================================================================

    public static final double BARRAS_ANCHO = 10;
    public static final double BARRAS_SEPARACION = 3;
    public static final double BARRAS_ALTO = 15;
    public static final double BARRAS_ESCALA_MINIMA = 0.2;
    public static final Duration BARRAS_DURACION = Duration.seconds(0.8);
    public static final String BARRAS_COLOR = "#96E0F7";

    /** Altura base de cada barra, como fraccion de {@link #BARRAS_ALTO}. */
    public static final double[] BARRAS_ALTURAS = {
        0.40, 0.70, 0.30, 0.90, 1.00, 0.50, 0.80, 0.60, 0.40
    };

    /** Retardo inicial de cada barra, en segundos. Es lo que las desincroniza. */
    public static final double[] BARRAS_RETARDOS = {
        0.1, 0.4, 0.2, 0.6, 0.1, 0.5, 0.3, 0.7, 0.2
    };

    // ==================================================================
    // Botones de reproduccion
    // ==================================================================

    /** Triangulo verde: la accion disponible es reproducir. */
    public static final String RUTA_BOTON_PLAY = "/imagenes/spidey/boton-play.png";

    /** Dos barras rojas: la accion disponible es pausar. */
    public static final String RUTA_BOTON_PAUSA = "/imagenes/spidey/boton-pausa.png";

    public static final int BOTON_LADO = 48;

    // ==================================================================
    // Senial: la insignia animada de la cabecera
    // Hoja medida: 3726 x 83 = 46 cuadros de 81 x 83
    // ==================================================================

    public static final String RUTA_SENIAL = "/imagenes/spidey/hoja-senial.png";
    public static final int SENIAL_CUADROS = 46;
    public static final int SENIAL_ANCHO_CUADRO = 81;
    public static final int SENIAL_ALTO_CUADRO = 83;
    public static final double SENIAL_FPS = 12;
}
