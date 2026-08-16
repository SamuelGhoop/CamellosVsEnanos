package com.eia.reproductor.servicios.spotify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del barrido de procesos huerfanos. */
class ProcesoLibrespotTest {
    @TempDir
    Path carpeta;

    @Test
    @DisplayName("Lee el identificador anotado en la sesión anterior")
    void leeElPidAnotado() throws IOException {
        Path archivo = carpeta.resolve("librespot.pid");
        Files.writeString(archivo, "27596");

        assertEquals(Optional.of(27596L), ProcesoLibrespot.leerPidGuardado(archivo));
    }

    @Test
    @DisplayName("Tolera espacios y saltos de línea alrededor")
    void toleraEspacios() throws IOException {
        Path archivo = carpeta.resolve("librespot.pid");
        Files.writeString(archivo, "  27596\n");

        assertEquals(Optional.of(27596L), ProcesoLibrespot.leerPidGuardado(archivo));
    }

    @Test
    @DisplayName("Sin archivo no hay nada que barrer")
    void sinArchivoNoHayNadaQueBarrer() {
        assertTrue(ProcesoLibrespot.leerPidGuardado(carpeta.resolve("no-existe.pid")).isEmpty());
    }

    @Test
    @DisplayName("Un archivo corrupto no rompe el arranque")
    void archivoCorrupto() throws IOException {
        Path archivo = carpeta.resolve("librespot.pid");
        Files.writeString(archivo, "esto no es un número");

        // Reventar aquí dejaría la aplicación sin arrancar por un archivo que se regenera solo.
        assertTrue(ProcesoLibrespot.leerPidGuardado(archivo).isEmpty());
    }

    @Test
    @DisplayName("Un archivo vacío tampoco")
    void archivoVacio() throws IOException {
        Path archivo = carpeta.resolve("librespot.pid");
        Files.writeString(archivo, "");

        assertTrue(ProcesoLibrespot.leerPidGuardado(archivo).isEmpty());
    }

    @Test
    @DisplayName("No mata un proceso que ya no es librespot")
    void noMataUnProcesoReutilizado() {
        // El sistema recicla los identificadores: el de la sesión anterior puede pertenecer ahora
        // a cualquier cosa. Esta es la comprobación que evita matar el programa equivocado.
        assertFalse(ProcesoLibrespot.pareceLibrespot(ProcessHandle.current()));
    }

    @Test
    @DisplayName("Encuentra el ejecutable instalado por cargo")
    void encuentraElEjecutable() {
        // En esta máquina está instalado; en otra sin librespot el Optional viene vacío y la
        // aplicación arranca igual, así que ambas respuestas son válidas.
        ProcesoLibrespot.localizarEjecutable().ifPresent(ruta ->
                assertTrue(ruta.toString().toLowerCase().contains("librespot")));
    }
}
