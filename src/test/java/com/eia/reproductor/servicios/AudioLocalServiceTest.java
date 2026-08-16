package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de la fuente de audio local. */
class AudioLocalServiceTest {
    private final AudioLocalService servicio = new AudioLocalService();

    private static Cancion conRuta(String ruta) {
        Cancion cancion = new Cancion("Titulo");
        cancion.setRutaArchivo(ruta);
        return cancion;
    }

    @Test
    @DisplayName("Acepta un MP3 que existe en el disco")
    void aceptaUnMp3Existente(@TempDir Path carpeta) throws IOException {
        Path archivo = Files.createFile(carpeta.resolve("cancion.mp3"));

        assertNotNull(AudioLocalService.archivoDe(conRuta(archivo.toString())));
        assertTrue(servicio.puedeReproducir(conRuta(archivo.toString())));
    }

    @Test
    @DisplayName("Acepta un WAV que existe en el disco")
    void aceptaUnWavExistente(@TempDir Path carpeta) throws IOException {
        Path archivo = Files.createFile(carpeta.resolve("efecto.wav"));

        assertTrue(servicio.puedeReproducir(conRuta(archivo.toString())));
    }

    @Test
    @DisplayName("La extension se compara sin importar mayusculas")
    void laExtensionNoDistingueMayusculas(@TempDir Path carpeta) throws IOException {
        Path archivo = Files.createFile(carpeta.resolve("CANCION.MP3"));

        assertTrue(servicio.puedeReproducir(conRuta(archivo.toString())));
    }

    @Test
    @DisplayName("Rechaza un formato que MediaPlayer no soporta")
    void rechazaUnFormatoNoSoportado(@TempDir Path carpeta) throws IOException {
        Path archivo = Files.createFile(carpeta.resolve("cancion.flac"));

        assertNull(AudioLocalService.archivoDe(conRuta(archivo.toString())));
        assertFalse(servicio.puedeReproducir(conRuta(archivo.toString())));
    }

    @Test
    @DisplayName("Rechaza una ruta que apunta a un archivo inexistente")
    void rechazaUnaRutaRota(@TempDir Path carpeta) {
        Cancion cancion = conRuta(carpeta.resolve("no-existe.mp3").toString());

        assertFalse(servicio.puedeReproducir(cancion));
    }

    @Test
    @DisplayName("Rechaza una ruta que apunta a una carpeta")
    void rechazaUnaCarpeta(@TempDir Path carpeta) {
        assertFalse(servicio.puedeReproducir(conRuta(carpeta.toString())));
    }

    @Test
    @DisplayName("Rechaza canciones sin ruta de archivo")
    void rechazaCancionesSinArchivo() {
        assertFalse(servicio.puedeReproducir(null));
        assertFalse(servicio.puedeReproducir(new Cancion("Solo metadata")));
        assertFalse(servicio.puedeReproducir(conRuta("")));
        assertFalse(servicio.puedeReproducir(conRuta("   ")));
    }

    @Test
    @DisplayName("Avisa cuando le mandan una cancion sin archivo reproducible")
    void avisaCuandoElArchivoNoSirve() {
        java.util.List<String> avisos = new java.util.ArrayList<>();
        servicio.setAlFallar(avisos::add);

        servicio.reproducir(conRuta("no-existe.mp3"));

        assertEquals(1, avisos.size());
        assertFalse(avisos.get(0).isBlank());
        assertFalse(servicio.reproduciendoProperty().get());
    }

    @Test
    @DisplayName("La fuente siempre esta disponible: no depende de nada externo")
    void siempreEstaDisponible() {
        assertTrue(servicio.disponible());
        assertFalse(servicio.nombreFuente().isBlank());
    }

    @Test
    @DisplayName("Detener sin haber reproducido nada no rompe ni deja estado sucio")
    void detenerSinReproducirEsInocuo() {
        servicio.detener();

        assertEquals(0L, servicio.posicionMsProperty().get());
        assertEquals(0L, servicio.duracionMsProperty().get());
        assertFalse(servicio.reproduciendoProperty().get());
    }
}
