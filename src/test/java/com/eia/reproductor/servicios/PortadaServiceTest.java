package com.eia.reproductor.servicios;

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

/** Pruebas del {@link PortadaService}. */
@DisplayName("Cache de caratulas")
class PortadaServiceTest {
    @TempDir
    Path carpeta;

    /** Crea un archivo que pasa por caratula valida. */
    private Path fabricarCaratula(String id) throws IOException {
        Path archivo = carpeta.resolve(id + ".jpg");
        Files.write(archivo, new byte[2048]);
        return archivo;
    }

    @Test
    @DisplayName("la caratula se guarda con el id de la cancion como nombre")
    void nombradaPorId() {
        PortadaService servicio = new PortadaService(carpeta);

        assertEquals(carpeta.resolve("abc-123.jpg"), servicio.rutaDe("abc-123"));
    }

    @Test
    @DisplayName("si ya esta descargada, se devuelve sin tocar la red")
    void noRedescarga() throws IOException {
        Path existente = fabricarCaratula("cancion-1");
        PortadaService servicio = new PortadaService(carpeta);

        // La URL es deliberadamente invalida: si el servicio intentara descargarla, fallaria.
        Optional<Path> obtenida = servicio.obtener("cancion-1", "http://no-existe.invalido/x.jpg");

        assertTrue(obtenida.isPresent());
        assertEquals(existente, obtenida.get());
        assertTrue(servicio.ultimoAviso().isEmpty());
    }

    @Test
    @DisplayName("cacheada() no sale a internet ni crea nada")
    void cacheadaEsSoloLectura() {
        PortadaService servicio = new PortadaService(carpeta);

        assertTrue(servicio.cacheada("no-existe").isEmpty());
        assertFalse(Files.exists(carpeta.resolve("no-existe.jpg")));
    }

    @Test
    @DisplayName("sin URL y sin cache no hay caratula, y no es un error")
    void sinUrlNiCache() {
        PortadaService servicio = new PortadaService(carpeta);

        assertTrue(servicio.obtener("cancion-x", null).isEmpty());
        assertTrue(servicio.obtener("cancion-x", "").isEmpty());
    }

    @Test
    @DisplayName("un id nulo o vacio no rompe nada")
    void idInvalido() {
        PortadaService servicio = new PortadaService(carpeta);

        assertTrue(servicio.cacheada(null).isEmpty());
        assertTrue(servicio.cacheada("").isEmpty());
    }

    @Test
    @DisplayName("borrar quita la caratula del disco")
    void borrar() throws IOException {
        fabricarCaratula("cancion-2");
        PortadaService servicio = new PortadaService(carpeta);

        assertTrue(servicio.borrar("cancion-2"));
        assertTrue(servicio.cacheada("cancion-2").isEmpty());
        assertFalse(servicio.borrar("cancion-2"), "borrar dos veces devuelve false la segunda");
    }

    @Test
    @DisplayName("una URL inalcanzable devuelve vacio con aviso, sin lanzar excepcion")
    void urlInalcanzable() {
        PortadaService servicio = new PortadaService(carpeta);

        // Dominio reservado por la RFC 2606: nunca resuelve, asi que no depende de que haya red.
        Optional<Path> obtenida = servicio.obtener("cancion-3", "http://servidor.invalid/portada.jpg");

        assertTrue(obtenida.isEmpty());
        assertTrue(servicio.ultimoAviso().isPresent(), "deberia quedar el motivo del fallo");
        assertFalse(Files.exists(carpeta.resolve("cancion-3.jpg")));
        assertFalse(Files.exists(carpeta.resolve("cancion-3.jpg.tmp")),
                "no debe quedar un temporal huerfano");
    }

    @Test
    @DisplayName("una URL con formato invalido tampoco lanza excepcion")
    void urlMalformada() {
        PortadaService servicio = new PortadaService(carpeta);

        assertTrue(servicio.obtener("cancion-4", "esto no es una url").isEmpty());
        assertTrue(servicio.ultimoAviso().isPresent());
    }
}
