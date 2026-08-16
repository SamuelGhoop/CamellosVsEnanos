package com.eia.reproductor.servicios.spotify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de la carga de {@code config/spotify.properties}. */
class ConfiguracionSpotifyTest {
    @TempDir
    Path carpeta;

    private Path escribir(String contenido) throws IOException {
        Path archivo = carpeta.resolve("spotify.properties");
        Files.writeString(archivo, contenido);
        return archivo;
    }

    @Test
    @DisplayName("Lee un archivo completo")
    void leeUnArchivoCompleto() throws IOException {
        Path archivo = escribir("""
                client.id=abc123
                redirect.uri=http://127.0.0.1:8888/callback
                device.name=Camellos vs Enanos
                """);

        ConfiguracionSpotify config = ConfiguracionSpotify.cargar(archivo).orElseThrow();

        assertEquals("abc123", config.clientId());
        assertEquals("http://127.0.0.1:8888/callback", config.redirectUri());
        assertEquals("Camellos vs Enanos", config.nombreDispositivo());
    }

    @Test
    @DisplayName("Sin archivo devuelve vacío, no excepción")
    void sinArchivoDevuelveVacio() {
        // Es el caso de todo el que no configuró Spotify: la app tiene que arrancar igual.
        assertTrue(ConfiguracionSpotify.cargar(carpeta.resolve("no-existe.properties")).isEmpty());
        assertTrue(ConfiguracionSpotify.cargar(null).isEmpty());
    }

    @Test
    @DisplayName("Un .example copiado y sin rellenar cuenta como no configurado")
    void archivoSinRellenarCuentaComoAusente() throws IOException {
        Path archivo = escribir("""
                client.id=
                redirect.uri=http://127.0.0.1:8888/callback
                """);

        assertTrue(ConfiguracionSpotify.cargar(archivo).isEmpty());
    }

    @Test
    @DisplayName("Falta el redirect uri: tampoco sirve")
    void sinRedirectUriNoSirve() throws IOException {
        Path archivo = escribir("client.id=abc123\n");

        assertTrue(ConfiguracionSpotify.cargar(archivo).isEmpty());
    }

    @Test
    @DisplayName("Recorta los espacios sobrantes")
    void recortaEspacios() throws IOException {
        Path archivo = escribir("""
                client.id=   abc123\s
                redirect.uri=  http://127.0.0.1:8888/callback\s
                """);

        ConfiguracionSpotify config = ConfiguracionSpotify.cargar(archivo).orElseThrow();

        assertEquals("abc123", config.clientId());
        assertEquals("http://127.0.0.1:8888/callback", config.redirectUri());
    }

    @Test
    @DisplayName("El nombre de dispositivo tiene valor por defecto")
    void nombreDeDispositivoPorDefecto() throws IOException {
        Path archivo = escribir("""
                client.id=abc123
                redirect.uri=http://127.0.0.1:8888/callback
                """);

        assertEquals("Camellos vs Enanos",
                ConfiguracionSpotify.cargar(archivo).orElseThrow().nombreDispositivo());
    }

    @Test
    @DisplayName("Saca el puerto y la ruta del redirect uri")
    void extraePuertoYRuta() throws IOException {
        Path archivo = escribir("""
                client.id=abc123
                redirect.uri=http://127.0.0.1:9999/volver
                """);

        ConfiguracionSpotify config = ConfiguracionSpotify.cargar(archivo).orElseThrow();

        // El servidor local tiene que levantarse justo donde dice el redirect registrado.
        assertEquals(9999, config.puertoDeRetorno());
        assertEquals("/volver", config.rutaDeRetorno());
    }

    @Test
    @DisplayName("Un redirect uri sin puerto ni ruta cae en los valores por defecto")
    void redirectSinPuertoNiRuta() throws IOException {
        Path archivo = escribir("""
                client.id=abc123
                redirect.uri=http://127.0.0.1
                """);

        ConfiguracionSpotify config = ConfiguracionSpotify.cargar(archivo).orElseThrow();

        assertEquals(8888, config.puertoDeRetorno());
        assertEquals("/callback", config.rutaDeRetorno());
    }

    @Test
    @DisplayName("Una carpeta en lugar de un archivo no rompe nada")
    void unaCarpetaNoRompe() {
        Optional<ConfiguracionSpotify> config = ConfiguracionSpotify.cargar(carpeta);

        assertTrue(config.isEmpty());
    }
}
