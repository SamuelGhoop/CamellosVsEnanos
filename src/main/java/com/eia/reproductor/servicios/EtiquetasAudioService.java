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

/** Lee las etiquetas ID3 de un archivo de audio local. */
public class EtiquetasAudioService {
    static {
        // jaudiotagger escribe muchisimo por consola al abrir cada archivo; se silencia para que
        // no tape los mensajes reales de la aplicacion.
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
    }

    /** Datos leidos de un archivo de audio. */
    public record Etiquetas(String titulo, String artista, String album, String genero,
                            int anio, int duracionSegundos) {
        /** Arma la consulta con la que se buscara en la API. */
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

    /** Lee las etiquetas de un archivo de audio. */
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
