package com.eia.reproductor.servicios.spotify;

/** Decide, a partir del sondeo, cuando una pista de Spotify termino de verdad. */
public class DetectorFinDePista {
    /** Cuanto antes del final se acepta como "termino". */
    public static final long MARGEN_FINAL_MS = 3_000;

    /** Respuestas coherentes seguidas que hacen falta para dar el fin por bueno. */
    public static final int LECTURAS_PARA_CONFIRMAR = 2;

    private String uriVigilada;
    private int coherentes;
    private boolean yaAvisado;
    private long posicionMaximaVista;
    private long duracionVista;

    /** Empieza a vigilar una pista nueva. */
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

    /** Incorpora una respuesta del sondeo. */
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

    /** Anota hasta donde ha llegado la pista. */
    private void recordar(EstadoReproductorSpotify estado) {
        if (estado == null || !uriVigilada.equals(estado.uriPista())) {
            return;
        }
        posicionMaximaVista = Math.max(posicionMaximaVista, estado.posicionMs());
        duracionVista = Math.max(duracionVista, estado.duracionMs());
    }

    /** Determina si una respuesta suelta es coherente con el final de la pista vigilada. */
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
