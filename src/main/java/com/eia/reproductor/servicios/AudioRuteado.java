package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fuente de audio que delega en otras, eligiendo la mejor para cada cancion.
 *
 * <p>Implementa {@link ReproductorAudio} y a la vez contiene varias: es el patron compuesto. El
 * controlador habla solo con esta y nunca sabe cuantas fuentes hay ni cuales.</p>
 *
 * <p><b>Por que existe.</b> Sin ella, el controlador tendria que preguntar "¿esta cancion tiene
 * archivo local? ¿tiene URI de Spotify? ¿esta librespot listo?" y elegir el mismo. Eso significa
 * que agregar Spotify obligaria a modificar el controlador. Con el enrutador, agregar una fuente
 * es registrarla en la lista y nada mas: ni el controlador, ni la interfaz grafica, ni las
 * estructuras de datos se enteran.</p>
 *
 * <p>El orden de registro es el orden de preferencia. Se recorre la lista y gana la primera fuente
 * que este disponible y sepa reproducir la cancion; la simulada va siempre al final porque acepta
 * cualquier cosa, y asi el reproductor nunca se queda sin fuente.</p>
 *
 * <p><b>Recuperacion ante fallos.</b> Si la fuente que esta sonando falla a mitad de la cancion, el
 * enrutador baja a la siguiente que sepa reproducirla y retoma en la posicion donde iba. La
 * busqueda continua siempre <i>hacia abajo</i> desde la que fallo, de modo que cada fuente se
 * intenta como maximo una vez por cancion: eso hace imposible el bucle infinito y garantiza que la
 * cadena termina en la fuente simulada, que acepta cualquier cosa.</p>
 */
public class AudioRuteado implements ReproductorAudio {

    /** Valor de {@link #indiceFuenteActiva} cuando todavia no hay ninguna fuente sonando. */
    private static final int SIN_FUENTE = -1;

    private final List<ReproductorAudio> fuentes = new ArrayList<>();
    private final ReadOnlyLongWrapper posicionMs = new ReadOnlyLongWrapper(0);
    private final ReadOnlyLongWrapper duracionMs = new ReadOnlyLongWrapper(0);
    private final BooleanProperty reproduciendo = new SimpleBooleanProperty(false);

    private int indiceFuenteActiva = SIN_FUENTE;
    private Cancion cancionActual;
    private boolean evitarRed;

    /** Volumen de 0 a 100, compartido por todas las fuentes. */
    private int volumen = 100;

    private Runnable alTerminarPista;
    private Consumer<String> alFallar;

    /**
     * Crea el enrutador con sus fuentes, en orden de preferencia.
     *
     * @param enOrdenDePreferencia fuentes a consultar; la ultima deberia aceptar cualquier cancion
     */
    public AudioRuteado(ReproductorAudio... enOrdenDePreferencia) {
        for (ReproductorAudio fuente : enOrdenDePreferencia) {
            agregarFuente(fuente);
        }
    }

    /**
     * Registra una fuente al final de la lista de preferencia.
     *
     * @param fuente fuente a registrar
     */
    public final void agregarFuente(ReproductorAudio fuente) {
        if (fuente == null) {
            return;
        }
        // Los avisos de las fuentes no salen directo hacia arriba: pasan por el enrutador, que es
        // quien decide si el fallo se puede remediar cambiando de fuente.
        fuente.setAlTerminarPista(() -> alTerminar(fuente));
        fuente.setAlFallar(mensaje -> alFallarFuente(fuente, mensaje));
        // Una fuente que se suma tarde —Spotify— tiene que nacer con el volumen ya elegido.
        fuente.setVolumen(volumen);
        fuentes.add(fuente);
    }

    /**
     * Registra una fuente en cabeza, por delante de las demas.
     *
     * <p>Pensado para Spotify: cuando esta disponible tiene que ganarle al archivo local.</p>
     *
     * @param fuente fuente prioritaria
     */
    public void agregarFuentePrioritaria(ReproductorAudio fuente) {
        agregarFuente(fuente);
        if (fuentes.size() > 1) {
            fuentes.add(0, fuentes.remove(fuentes.size() - 1));
            if (indiceFuenteActiva != SIN_FUENTE) {
                indiceFuenteActiva++;
            }
        }
    }

    @Override
    public void reproducir(Cancion cancion) {
        cancionActual = cancion;
        int elegida = indiceDeFuentePara(cancion, 0);
        if (elegida == SIN_FUENTE) {
            return;
        }
        activar(elegida, 0);
    }

    @Override
    public void pausar() {
        if (fuenteActiva() != null) {
            fuenteActiva().pausar();
        }
    }

    @Override
    public void reanudar() {
        if (fuenteActiva() != null) {
            fuenteActiva().reanudar();
        }
    }

    @Override
    public void detener() {
        if (fuenteActiva() != null) {
            fuenteActiva().detener();
        }
    }

    @Override
    public void buscarPosicion(long milisegundos) {
        if (fuenteActiva() != null) {
            fuenteActiva().buscarPosicion(milisegundos);
        }
    }

    @Override
    public void avanzarRelativo(long milisegundos) {
        if (fuenteActiva() != null) {
            fuenteActiva().avanzarRelativo(milisegundos);
        }
    }

    @Override
    public ReadOnlyLongProperty posicionMsProperty() {
        return posicionMs.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyLongProperty duracionMsProperty() {
        return duracionMs.getReadOnlyProperty();
    }

    @Override
    public BooleanProperty reproduciendoProperty() {
        return reproduciendo;
    }

    @Override
    public boolean disponible() {
        return fuentes.stream().anyMatch(this::utilizable);
    }

    @Override
    public boolean puedeReproducir(Cancion cancion) {
        return indiceDeFuentePara(cancion, 0) != SIN_FUENTE;
    }

    /** @return el nombre de la fuente que esta sonando, para mostrarlo en la interfaz */
    @Override
    public String nombreFuente() {
        return fuenteActiva() == null ? "Sin fuente" : fuenteActiva().nombreFuente();
    }

    @Override
    public void setAlTerminarPista(Runnable callback) {
        this.alTerminarPista = callback;
    }

    @Override
    public void setAlFallar(Consumer<String> callback) {
        this.alFallar = callback;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Se aplica a <b>todas</b> las fuentes, no solo a la que suena: si se cambia de fuente a
     * mitad de sesion, la nueva tiene que arrancar al volumen que el usuario dejo puesto.</p>
     */
    @Override
    public void setVolumen(int porcentaje) {
        volumen = Math.max(0, Math.min(100, porcentaje));
        fuentes.forEach(fuente -> fuente.setVolumen(volumen));
    }

    /** @return el volumen actual, de 0 a 100 */
    public int volumen() {
        return volumen;
    }

    /**
     * {@inheritDoc}
     *
     * <p>En el enrutador esto si tiene efecto: las fuentes que dependen de la red quedan fuera de
     * la seleccion. Es la valvula de seguridad para cuando la conexion no acompana.</p>
     */
    @Override
    public void setEvitarRed(boolean evitar) {
        if (this.evitarRed == evitar) {
            return;
        }
        this.evitarRed = evitar;

        // Si la fuente que esta sonando acaba de quedar prohibida, hay que bajarse de ella en el
        // acto: de nada sirve la politica si la cancion en curso sigue saliendo por la red.
        if (fuenteActiva() == null || utilizable(fuenteActiva())) {
            return;
        }
        String anterior = fuenteActiva().nombreFuente();
        String reemplazo = bajarASiguienteFuente();
        if (reemplazo == null) {
            // Quedarse callado seria lo peor: el usuario pidio algo y hay que decirle que no se
            // pudo, en vez de dejar sonando justo la fuente que pidio evitar.
            detener();
            avisar("No hay ninguna fuente sin conexión para esta canción.");
            return;
        }
        avisar("Se pasó de " + anterior + " a " + reemplazo + ".");
    }

    /** @return {@code true} si se estan evitando las fuentes que dependen de la red */
    public boolean evitandoRed() {
        return evitarRed;
    }

    // ------------------------------------------------------------------
    // Seleccion de fuente
    // ------------------------------------------------------------------

    private ReproductorAudio fuenteActiva() {
        return indiceFuenteActiva == SIN_FUENTE ? null : fuentes.get(indiceFuenteActiva);
    }

    /** @return {@code true} si la fuente se puede usar con la politica actual */
    private boolean utilizable(ReproductorAudio fuente) {
        return fuente.disponible() && !(evitarRed && fuente.requiereRed());
    }

    /**
     * Busca la primera fuente capaz de reproducir la cancion a partir de una posicion de la lista.
     *
     * @param cancion cancion a reproducir
     * @param desde   indice desde el que empezar a mirar
     * @return el indice de la fuente elegida, o {@link #SIN_FUENTE} si ninguna sirve
     */
    private int indiceDeFuentePara(Cancion cancion, int desde) {
        for (int i = Math.max(0, desde); i < fuentes.size(); i++) {
            ReproductorAudio fuente = fuentes.get(i);
            if (utilizable(fuente) && fuente.puedeReproducir(cancion)) {
                return i;
            }
        }
        return SIN_FUENTE;
    }

    /**
     * Pone a sonar una fuente concreta y ata la interfaz a ella.
     *
     * @param indice           fuente a activar
     * @param posicionARetomar milisegundos desde donde continuar; 0 para empezar de cero
     */
    private void activar(int indice, long posicionARetomar) {
        ReproductorAudio anterior = fuenteActiva();
        ReproductorAudio nueva = fuentes.get(indice);

        if (anterior != null && anterior != nueva) {
            anterior.detener();
            desatar();
        }
        if (anterior != nueva) {
            indiceFuenteActiva = indice;
            atar(nueva);
        }
        nueva.reproducir(cancionActual);
        if (posicionARetomar > 0) {
            retomarEn(nueva, posicionARetomar);
        }
    }

    /**
     * Baja a la primera fuente posterior a la actual que sepa reproducir la cancion.
     *
     * <p>La busqueda arranca <i>despues</i> de la fuente en curso, nunca desde el principio: por
     * eso cada fuente se prueba como maximo una vez por cancion y la cadena no puede ciclar. Como
     * la ultima fuente registrada acepta cualquier cosa, en la practica siempre hay donde caer.</p>
     *
     * @return el nombre de la fuente nueva, o {@code null} si ya no quedaba ninguna
     */
    private String bajarASiguienteFuente() {
        // Se lee antes de activar nada: al desatar, la propiedad conserva el ultimo valor de la
        // fuente que se va, que es justo la posicion que hay que retomar.
        long posicionRecordada = posicionMs.get();
        int reemplazo = indiceDeFuentePara(cancionActual, indiceFuenteActiva + 1);
        if (reemplazo == SIN_FUENTE) {
            return null;
        }
        activar(reemplazo, posicionRecordada);
        return fuentes.get(reemplazo).nombreFuente();
    }

    /**
     * Salta a la posicion recordada en cuanto la fuente nueva sepa cuanto dura la pista.
     *
     * <p>No se puede saltar de inmediato: un MP3 no publica su duracion hasta que carga la
     * cabecera, y un salto contra una duracion de cero se recorta a cero y perderia el avance.</p>
     */
    private void retomarEn(ReproductorAudio fuente, long posicionARetomar) {
        if (fuente.duracionMsProperty().get() > 0) {
            fuente.buscarPosicion(posicionARetomar);
            return;
        }
        fuente.duracionMsProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable,
                                Number anterior, Number actual) {
                if (actual.longValue() > 0) {
                    fuente.duracionMsProperty().removeListener(this);
                    fuente.buscarPosicion(posicionARetomar);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Avisos de las fuentes
    // ------------------------------------------------------------------

    private void alTerminar(ReproductorAudio fuente) {
        // Solo cuenta el final de la fuente que esta sonando: una fuente vieja que se apaga tarde
        // no debe hacer avanzar la cola.
        if (fuente == fuenteActiva() && alTerminarPista != null) {
            alTerminarPista.run();
        }
    }

    private void alFallarFuente(ReproductorAudio fuente, String mensaje) {
        if (fuente != fuenteActiva()) {
            avisar(mensaje);
            return;
        }
        String nombreQueFallo = fuente.nombreFuente();
        String reemplazo = bajarASiguienteFuente();
        avisar(reemplazo == null
                ? mensaje
                : mensaje + " Se continuó en " + reemplazo + " (falló " + nombreQueFallo + ").");
    }

    private void avisar(String mensaje) {
        if (mensaje != null && alFallar != null) {
            alFallar.accept(mensaje);
        }
    }

    // ------------------------------------------------------------------
    // Propiedades observables
    // ------------------------------------------------------------------

    /**
     * Reexpone las propiedades de la fuente activa como propias.
     *
     * <p>Asi la interfaz se ata una sola vez al enrutador y sigue funcionando aunque por debajo se
     * cambie de fuente a mitad de sesion.</p>
     */
    private void atar(ReproductorAudio fuente) {
        posicionMs.bind(fuente.posicionMsProperty());
        duracionMs.bind(fuente.duracionMsProperty());
        reproduciendo.bind(fuente.reproduciendoProperty());
    }

    private void desatar() {
        // Los valores quedan congelados en el ultimo dato de la fuente que se va, que es justo la
        // posicion que hay que retomar en la fuente nueva.
        posicionMs.unbind();
        duracionMs.unbind();
        reproduciendo.unbind();
    }
}
