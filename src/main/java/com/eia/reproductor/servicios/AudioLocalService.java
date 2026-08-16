package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.File;
import java.util.Locale;
import java.util.function.Consumer;

/** Reproduce archivos MP3 y WAV del disco con {@link MediaPlayer}. */
public class AudioLocalService implements ReproductorAudio {
    private static final String[] EXTENSIONES = {".mp3", ".wav"};

    /** Cada cuanto entrega MediaPlayer un analisis nuevo. */
    private static final double INTERVALO_ESPECTRO_SEGUNDOS = 0.05;

    /** Por debajo de este nivel se considera silencio. */
    private static final int UMBRAL_ESPECTRO_DB = -60;

    /** Curva que realza los niveles bajos. */
    private static final double EXPONENTE_REALCE = 0.6;

    private final ReadOnlyLongWrapper posicionMs = new ReadOnlyLongWrapper(0);
    private final ReadOnlyLongWrapper duracionMs = new ReadOnlyLongWrapper(0);
    private final BooleanProperty reproduciendo = new SimpleBooleanProperty(false);

    private MediaPlayer reproductor;
    private Runnable alTerminarPista;
    private Consumer<String> alFallar;

    /** Nivel de 0 a 1. */
    private double volumen = 1.0;

    private Consumer<double[]> oyenteDelEspectro;
    private int bandasDelEspectro = 9;

    @Override
    public void reproducir(Cancion cancion) {
        detener();
        File archivo = archivoDe(cancion);
        if (archivo == null) {
            avisar("La canción no tiene un archivo de audio válido.");
            return;
        }
        try {
            reproductor = new MediaPlayer(new Media(archivo.toURI().toString()));

            // La duracion real solo se conoce cuando el medio termina de cargar su cabecera.
            reproductor.setOnReady(() -> duracionMs.set(aMilisegundos(reproductor.getTotalDuration())));
            reproductor.currentTimeProperty().addListener(
                    (observable, anterior, actual) -> posicionMs.set(aMilisegundos(actual)));
            reproductor.setOnEndOfMedia(() -> {
                reproduciendo.set(false);
                if (alTerminarPista != null) {
                    alTerminarPista.run();
                }
            });
            reproductor.setOnError(() -> {
                avisar("No se pudo reproducir " + archivo.getName() + ": "
                        + reproductor.getError().getMessage());
                reproduciendo.set(false);
            });

            // El analisis se engancha al reproductor nuevo: se crea uno por cancion.
            conectarEspectro(reproductor);
            reproductor.setVolume(volumen);
            reproductor.play();
            reproduciendo.set(true);
        } catch (Exception excepcion) {
            avisar("No se pudo abrir " + archivo.getName() + ": " + excepcion.getMessage());
            reproductor = null;
            reproduciendo.set(false);
        }
    }

    @Override
    public void pausar() {
        if (reproductor != null) {
            reproductor.pause();
        }
        reproduciendo.set(false);
    }

    @Override
    public void reanudar() {
        if (reproductor != null) {
            reproductor.play();
            reproduciendo.set(true);
        }
    }

    @Override
    public void detener() {
        if (reproductor != null) {
            reproductor.stop();
            // Liberar el reproductor es obligatorio: cada MediaPlayer retiene un hilo y memoria
            // nativa, y encadenar canciones sin soltarlos termina agotando los recursos.
            reproductor.dispose();
            reproductor = null;
        }
        reproduciendo.set(false);
        posicionMs.set(0);
        duracionMs.set(0);
    }

    @Override
    public void buscarPosicion(long milisegundos) {
        if (reproductor == null) {
            return;
        }
        long limitada = Math.max(0, Math.min(milisegundos, duracionMs.get()));
        reproductor.seek(Duration.millis(limitada));
        posicionMs.set(limitada);
    }

    @Override
    public void avanzarRelativo(long milisegundos) {
        buscarPosicion(posicionMs.get() + milisegundos);
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
        return true;
    }

    @Override
    public boolean puedeReproducir(Cancion cancion) {
        return archivoDe(cancion) != null;
    }

    @Override
    public String nombreFuente() {
        return "Archivo local";
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
     * {@inheritDoc} Aqui si se puede: {@code MediaPlayer} hace el analisis de frecuencias por su
     * cuenta y entrega las magnitudes ya calculadas.
     */
    @Override
    public void setAlAnalizarEspectro(Consumer<double[]> oyente, int bandas) {
        this.oyenteDelEspectro = oyente;
        this.bandasDelEspectro = Math.max(1, bandas);
        if (reproductor != null) {
            conectarEspectro(reproductor);
        }
    }

    /** {@inheritDoc} El audio se decodifica aqui dentro, asi que hay muestras que mirar. */
    @Override
    public boolean analizaEspectro() {
        return true;
    }

    /** Engancha el analisis de frecuencias a un reproductor recien creado. */
    private void conectarEspectro(MediaPlayer nuevo) {
        if (oyenteDelEspectro == null) {
            return;
        }
        nuevo.setAudioSpectrumNumBands(bandasDelEspectro);
        nuevo.setAudioSpectrumInterval(INTERVALO_ESPECTRO_SEGUNDOS);
        nuevo.setAudioSpectrumThreshold(UMBRAL_ESPECTRO_DB);
        nuevo.setAudioSpectrumListener((tiempo, duracion, magnitudes, fases) -> {
            double[] niveles = new double[magnitudes.length];
            for (int i = 0; i < magnitudes.length; i++) {
                // De decibelios a fraccion: el umbral es el silencio y 0 dB el maximo.
                double fraccion = (magnitudes[i] - UMBRAL_ESPECTRO_DB) / -UMBRAL_ESPECTRO_DB;
                fraccion = Math.max(0, Math.min(1, fraccion));
                // La escala de decibelios deja casi toda la musica apretada en la parte baja y
                // las barras apenas se despegaban del suelo. La curva reparte mejor ese tramo,
                niveles[i] = Math.pow(fraccion, EXPONENTE_REALCE);
            }
            oyenteDelEspectro.accept(niveles);
        });
    }

    /**
     * {@inheritDoc} Se guarda el nivel aunque no haya nada sonando, porque {@code MediaPlayer} se
     * crea de nuevo con cada cancion: sin recordarlo, la siguiente arrancaria al volumen por
     * defecto.
     */
    @Override
    public void setVolumen(int porcentaje) {
        volumen = Math.max(0, Math.min(100, porcentaje)) / 100.0;
        if (reproductor != null) {
            reproductor.setVolume(volumen);
        }
    }

    private void avisar(String mensaje) {
        if (alFallar != null) {
            alFallar.accept(mensaje);
        }
    }

    /** Devuelve el archivo de la cancion si existe y tiene una extension soportada. */
    static File archivoDe(Cancion cancion) {
        if (cancion == null || cancion.getRutaArchivo() == null
                || cancion.getRutaArchivo().isBlank()) {
            return null;
        }
        File archivo = new File(cancion.getRutaArchivo());
        if (!archivo.isFile()) {
            return null;
        }
        String nombre = archivo.getName().toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONES) {
            if (nombre.endsWith(extension)) {
                return archivo;
            }
        }
        return null;
    }

    private static long aMilisegundos(Duration duracion) {
        if (duracion == null || duracion.isUnknown() || duracion.isIndefinite()) {
            return 0;
        }
        return (long) duracion.toMillis();
    }
}
