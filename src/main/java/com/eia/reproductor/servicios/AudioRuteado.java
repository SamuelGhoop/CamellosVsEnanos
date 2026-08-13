package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.SimpleBooleanProperty;

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
 */
public class AudioRuteado implements ReproductorAudio {

    private final List<ReproductorAudio> fuentes = new ArrayList<>();
    private final ReadOnlyLongWrapper posicionMs = new ReadOnlyLongWrapper(0);
    private final ReadOnlyLongWrapper duracionMs = new ReadOnlyLongWrapper(0);
    private final BooleanProperty reproduciendo = new SimpleBooleanProperty(false);

    private ReproductorAudio fuenteActiva;
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
     * <p>Es el unico punto que hay que tocar para sumar una fuente nueva.</p>
     *
     * @param fuente fuente a registrar
     */
    public final void agregarFuente(ReproductorAudio fuente) {
        if (fuente == null) {
            return;
        }
        // El aviso de fin de pista se reenvia hacia arriba: el enrutador es quien habla con el
        // controlador, no las fuentes por separado.
        fuente.setAlTerminarPista(() -> {
            if (alTerminarPista != null) {
                alTerminarPista.run();
            }
        });
        fuente.setAlFallar(mensaje -> {
            if (alFallar != null) {
                alFallar.accept(mensaje);
            }
        });
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
        }
    }

    @Override
    public void reproducir(Cancion cancion) {
        ReproductorAudio elegida = elegirPara(cancion);
        if (elegida == null) {
            return;
        }
        if (fuenteActiva != null && fuenteActiva != elegida) {
            fuenteActiva.detener();
            desatar();
        }
        if (fuenteActiva != elegida) {
            fuenteActiva = elegida;
            atar(fuenteActiva);
        }
        fuenteActiva.reproducir(cancion);
    }

    @Override
    public void pausar() {
        if (fuenteActiva != null) {
            fuenteActiva.pausar();
        }
    }

    @Override
    public void reanudar() {
        if (fuenteActiva != null) {
            fuenteActiva.reanudar();
        }
    }

    @Override
    public void detener() {
        if (fuenteActiva != null) {
            fuenteActiva.detener();
        }
    }

    @Override
    public void buscarPosicion(long milisegundos) {
        if (fuenteActiva != null) {
            fuenteActiva.buscarPosicion(milisegundos);
        }
    }

    @Override
    public void avanzarRelativo(long milisegundos) {
        if (fuenteActiva != null) {
            fuenteActiva.avanzarRelativo(milisegundos);
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
        return fuentes.stream().anyMatch(ReproductorAudio::disponible);
    }

    @Override
    public boolean puedeReproducir(Cancion cancion) {
        return elegirPara(cancion) != null;
    }

    /** @return el nombre de la fuente que esta sonando, para mostrarlo en la interfaz */
    @Override
    public String nombreFuente() {
        return fuenteActiva == null ? "Sin fuente" : fuenteActiva.nombreFuente();
    }

    @Override
    public void setAlTerminarPista(Runnable callback) {
        this.alTerminarPista = callback;
    }

    @Override
    public void setAlFallar(Consumer<String> callback) {
        this.alFallar = callback;
    }

    // ------------------------------------------------------------------
    // Apoyo interno
    // ------------------------------------------------------------------

    private ReproductorAudio elegirPara(Cancion cancion) {
        for (ReproductorAudio fuente : fuentes) {
            if (fuente.disponible() && fuente.puedeReproducir(cancion)) {
                return fuente;
            }
        }
        return null;
    }

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
        posicionMs.unbind();
        duracionMs.unbind();
        reproduciendo.unbind();
    }
}
