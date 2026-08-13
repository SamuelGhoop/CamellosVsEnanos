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

/**
 * Reproduce archivos MP3 y WAV del disco con {@link MediaPlayer}.
 *
 * <p>Es la fuente que cubre la bonificacion de "reproduccion real" del enunciado, y no necesita
 * ninguna dependencia externa: {@code javafx-media} ya esta en el proyecto.</p>
 *
 * <p><b>Excepcion a la regla de capas.</b> Este es el unico archivo de {@code servicios} que
 * importa {@code javafx.scene.*}, porque {@code MediaPlayer} vive en {@code javafx.scene.media} y
 * no existe alternativa en el JDK. Aun asi no toca la interfaz grafica: publica su avance por
 * propiedades observables y quien quiera pintarlo se ata a ellas.</p>
 *
 * <p><b>Sobre la duracion.</b> Un MP3 no conoce su duracion hasta que se lee su cabecera, asi que
 * al principio vale cero y se rellena en {@code setOnReady}. La barra de progreso tiene que
 * tolerar ese instante en que la duracion todavia no se sabe.</p>
 */
public class AudioLocalService implements ReproductorAudio {

    private static final String[] EXTENSIONES = {".mp3", ".wav"};

    private final ReadOnlyLongWrapper posicionMs = new ReadOnlyLongWrapper(0);
    private final ReadOnlyLongWrapper duracionMs = new ReadOnlyLongWrapper(0);
    private final BooleanProperty reproduciendo = new SimpleBooleanProperty(false);

    private MediaPlayer reproductor;
    private Runnable alTerminarPista;
    private Consumer<String> alFallar;

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

    private void avisar(String mensaje) {
        if (alFallar != null) {
            alFallar.accept(mensaje);
        }
    }

    /**
     * Devuelve el archivo de la cancion si existe y tiene una extension soportada.
     *
     * @return el archivo, o {@code null} si no sirve
     */
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
