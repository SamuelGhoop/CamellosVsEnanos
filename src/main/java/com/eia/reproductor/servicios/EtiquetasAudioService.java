package com.eia.reproductor.servicios;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lee las etiquetas ID3 de un archivo de audio local.
 *
 * <p>Es el primer paso de la via "agregar desde archivo": antes de salir a internet se mira que
 * dice el propio MP3 sobre si mismo. Muchos archivos ya traen titulo, artista, album, anio y
 * genero, y con eso se arma la consulta a la API en vez de obligar al usuario a escribirla.</p>
 *
 * <p>La duracion la da la cabecera del audio, no las etiquetas, asi que es fiable incluso en
 * archivos sin ningun tag.</p>
 *
 * <p>Un archivo sin etiquetas, corrupto o de un formato que la libreria no entienda no es un
 * error: se devuelve lo que se haya podido leer, o vacio, y el usuario completa el resto.</p>
 */
public class EtiquetasAudioService {

    static {
        // jaudiotagger escribe muchisimo por consola al abrir cada archivo; se silencia para que
        // no tape los mensajes reales de la aplicacion.
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
    }

    /**
     * Datos leidos de un archivo de audio.
     *
     * @param titulo           titulo segun las etiquetas, o {@code null}
     * @param artista          artista segun las etiquetas, o {@code null}
     * @param album            album segun las etiquetas, o {@code null}
     * @param genero           genero segun las etiquetas, o {@code null}
     * @param anio             anio de lanzamiento, 0 si no se pudo leer
     * @param duracionSegundos duracion real del audio segun su cabecera
     */
    public record Etiquetas(String titulo, String artista, String album, String genero,
                            int anio, int duracionSegundos) {

        /**
         * Arma la consulta con la que se buscara en la API.
         *
         * @param archivo archivo del que provienen las etiquetas, para usar su nombre de respaldo
         * @return una consulta razonable, nunca vacia
         */
        public String consultaSugerida(Path archivo) {
            String texto = ((artista == null ? "" : artista) + " "
                    + (titulo == null ? "" : titulo)).trim();
            if (!texto.isBlank()) {
                return texto;
            }
            // Sin etiquetas utiles, el nombre del archivo suele ser "Artista - Titulo.mp3".
            return nombreSinExtension(archivo);
        }
    }

    /**
     * Lee las etiquetas de un archivo de audio.
     *
     * @param archivo ruta al MP3 o WAV
     * @return las etiquetas leidas, o vacio si el archivo no se pudo abrir
     */
    public Optional<Etiquetas> leer(Path archivo) {
        if (archivo == null || !archivo.toFile().isFile()) {
            return Optional.empty();
        }
        try {
            AudioFile audio = AudioFileIO.read(new File(archivo.toString()));
            int duracion = audio.getAudioHeader() == null ? 0 : audio.getAudioHeader().getTrackLength();

            Tag etiquetas = audio.getTag();
            if (etiquetas == null) {
                // Archivo sin tags: al menos se conoce la duracion real.
                return Optional.of(new Etiquetas(null, null, null, null, 0, duracion));
            }

            return Optional.of(new Etiquetas(
                    valor(etiquetas, FieldKey.TITLE),
                    valor(etiquetas, FieldKey.ARTIST),
                    valor(etiquetas, FieldKey.ALBUM),
                    valor(etiquetas, FieldKey.GENRE),
                    anio(valor(etiquetas, FieldKey.YEAR)),
                    duracion));
        } catch (Exception excepcion) {
            // La libreria lanza una familia amplia de excepciones propias y de E/S. Ninguna debe
            // impedir que el usuario agregue la cancion a mano.
            return Optional.empty();
        }
    }

    private static String valor(Tag etiquetas, FieldKey campo) {
        try {
            String texto = etiquetas.getFirst(campo);
            return (texto == null || texto.isBlank()) ? null : texto.trim();
        } catch (Exception excepcion) {
            return null;
        }
    }

    /** El campo de anio a veces trae la fecha completa, por ejemplo {@code "1975-10-31"}. */
    private static int anio(String texto) {
        if (texto == null || texto.length() < 4) {
            return 0;
        }
        try {
            return Integer.parseInt(texto.substring(0, 4));
        } catch (NumberFormatException excepcion) {
            return 0;
        }
    }

    static String nombreSinExtension(Path archivo) {
        String nombre = archivo.getFileName().toString();
        int punto = nombre.lastIndexOf('.');
        return punto > 0 ? nombre.substring(0, punto) : nombre;
    }
}
