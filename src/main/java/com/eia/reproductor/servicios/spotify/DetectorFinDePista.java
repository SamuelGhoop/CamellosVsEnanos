package com.eia.reproductor.servicios.spotify;

/**
 * Decide, a partir del sondeo, cuando una pista de Spotify termino de verdad.
 *
 * <p><b>Por que es una clase aparte.</b> Spotify no avisa que una pista acabo: hay que deducirlo
 * mirando respuestas sucesivas de {@code GET /v1/me/player}. Esa deduccion tiene trampas, y
 * aislarla aqui permite probarla entera sin red ni cuenta, que es la unica forma de tener confianza
 * en algo que solo falla en vivo.</p>
 *
 * <p><b>La trampa principal.</b> Las lecturas de la API <i>no son monotonas</i>: en una prueba real
 * la posicion dio 1604 → 3284 → 759 ms sin que la pista se hubiera reiniciado (el registro de
 * librespot mostraba una sola carga). Por eso aqui no se usa nunca "la posicion retrocedio" como
 * senial de fin: un hipo de la API saltaria de cancion sola.</p>
 *
 * <p><b>Lo que Spotify hace de verdad al terminar.</b> Medido en vivo:</p>
 * <pre>
 * pos=188901/189239  sonando=true    ← último instante de audio
 * pos=209/189239     sonando=false   ← terminó: rebobina a cero y deja la pista cargada
 * </pre>
 * <p>O sea que al final la posicion <b>no</b> se queda pegada a la duracion: vuelve a cero. Por eso
 * la regla no puede ser "detenida y cerca del final" —eso no ocurre nunca— sino "detenida despues
 * de haber llegado cerca del final". Se recuerda la posicion maxima vista, y esa memoria es lo que
 * distingue el final de una pausa del usuario a mitad de cancion.</p>
 *
 * <p><b>La regla.</b> Se da por terminada cuando hay {@link #LECTURAS_PARA_CONFIRMAR} respuestas
 * seguidas coherentes con el final. Una lectura que contradice el final reinicia la cuenta. Y se
 * avisa una sola vez por pista, para que un sondeo lento no encadene dos saltos.</p>
 */
public class DetectorFinDePista {

    /**
     * Cuanto antes del final se acepta como "termino".
     *
     * <p>La posicion casi nunca llega exactamente a la duracion: el sondeo cae en algun punto y el
     * ultimo tramo se pierde. Tres segundos cubren un intervalo de sondeo normal sin llegar a
     * cortar musica que todavia suena.</p>
     */
    public static final long MARGEN_FINAL_MS = 3_000;

    /** Respuestas coherentes seguidas que hacen falta para dar el fin por bueno. */
    public static final int LECTURAS_PARA_CONFIRMAR = 2;

    private String uriVigilada;
    private int coherentes;
    private boolean yaAvisado;
    private long posicionMaximaVista;
    private long duracionVista;

    /**
     * Empieza a vigilar una pista nueva.
     *
     * @param uriPista pista que se acaba de mandar a reproducir
     */
    public void vigilar(String uriPista) {
        this.uriVigilada = uriPista;
        reiniciarMemoria();
    }

    /** Deja de vigilar; ninguna lectura posterior disparara el fin. */
    public void olvidar() {
        this.uriVigilada = null;
        reiniciarMemoria();
    }

    private void reiniciarMemoria() {
        this.coherentes = 0;
        this.yaAvisado = false;
        this.posicionMaximaVista = 0;
        this.duracionVista = 0;
    }

    /**
     * Incorpora una respuesta del sondeo.
     *
     * @param estado lo que devolvio la API, o {@code null} si respondio sin contenido
     * @return {@code true} la unica vez que se confirma que la pista termino
     */
    public boolean observar(EstadoReproductorSpotify estado) {
        if (uriVigilada == null || yaAvisado) {
            return false;
        }
        recordar(estado);
        if (pareceElFinal(estado)) {
            coherentes++;
        } else {
            // Cualquier senial de que sigue viva descarta lo acumulado: solo cuentan las
            // confirmaciones seguidas.
            coherentes = 0;
        }
        if (coherentes < LECTURAS_PARA_CONFIRMAR) {
            return false;
        }
        yaAvisado = true;
        return true;
    }

    /**
     * Anota hasta donde ha llegado la pista.
     *
     * <p>Esta memoria es el nucleo del detector: cuando Spotify rebobina al terminar, la posicion
     * del momento ya no sirve de nada y lo unico que queda es saber que en algun momento se llego
     * al final.</p>
     */
    private void recordar(EstadoReproductorSpotify estado) {
        if (estado == null || !uriVigilada.equals(estado.uriPista())) {
            return;
        }
        posicionMaximaVista = Math.max(posicionMaximaVista, estado.posicionMs());
        duracionVista = Math.max(duracionVista, estado.duracionMs());
    }

    /**
     * Determina si una respuesta suelta es coherente con el final de la pista vigilada.
     *
     * @param estado respuesta del sondeo, admite {@code null}
     * @return {@code true} si encaja con que la pista haya terminado
     */
    private boolean pareceElFinal(EstadoReproductorSpotify estado) {
        // Spotify paso a otra pista por su cuenta. No deberia pasar con autoplay apagado y una
        // sola uri, pero si pasa, la nuestra evidentemente termino.
        if (estado != null && estado.uriPista() != null
                && !estado.uriPista().equals(uriVigilada)) {
            return true;
        }
        // Mientras suene no hay nada que decidir.
        if (estado != null && estado.reproduciendo()) {
            return false;
        }
        // Detenida (o sin reproduccion activa) despues de haber llegado al final. Sin la segunda
        // mitad, una pausa del usuario a mitad de cancion saltaria de pista.
        return llegoAlFinal();
    }

    /** @return {@code true} si en algun sondeo la pista llego cerca de su duracion */
    private boolean llegoAlFinal() {
        return duracionVista > 0 && posicionMaximaVista >= duracionVista - MARGEN_FINAL_MS;
    }
}
