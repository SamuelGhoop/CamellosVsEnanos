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

/** Pruebas del guardado del token en disco. */
class AlmacenTokenSpotifyTest {
    @TempDir
    Path carpeta;

    private Path archivo() {
        return carpeta.resolve("token-spotify.json");
    }

    @Test
    @DisplayName("Guardar y volver a cargar conserva los tres campos")
    void viajeDeIdaYVuelta() {
        AlmacenTokenSpotify almacen = new AlmacenTokenSpotify(archivo());
        TokenSpotify original = new TokenSpotify("acceso-abc", "refresco-xyz", 1_800_000_000_000L);

        assertTrue(almacen.guardar(original));
        TokenSpotify copia = almacen.cargar().orElseThrow();

        assertEquals(original.accessToken(), copia.accessToken());
        assertEquals(original.refreshToken(), copia.refreshToken());
        assertEquals(original.venceEnMillis(), copia.venceEnMillis());
    }

    @Test
    @DisplayName("Sin archivo devuelve vacío: es el primer arranque, no un error")
    void sinArchivoDevuelveVacio() {
        assertTrue(new AlmacenTokenSpotify(archivo()).cargar().isEmpty());
    }

    @Test
    @DisplayName("Crea la carpeta si no existe")
    void creaLaCarpeta() {
        Path anidado = carpeta.resolve("sub").resolve("token.json");
        AlmacenTokenSpotify almacen = new AlmacenTokenSpotify(anidado);

        assertTrue(almacen.guardar(new TokenSpotify("a", "r", 1)));
        assertTrue(Files.isRegularFile(anidado));
    }

    @Test
    @DisplayName("Un archivo corrupto se trata como si no hubiera token")
    void archivoCorrupto() throws IOException {
        Files.writeString(archivo(), "{ esto no es json");

        // Reventar aqui dejaria la app sin arrancar por un archivo que se puede regenerar
        // simplemente volviendo a autorizar.
        assertTrue(new AlmacenTokenSpotify(archivo()).cargar().isEmpty());
    }

    @Test
    @DisplayName("Un JSON válido pero vacío tampoco cuenta como token")
    void jsonSinCampos() throws IOException {
        Files.writeString(archivo(), "{}");

        assertTrue(new AlmacenTokenSpotify(archivo()).cargar().isEmpty());
    }

    @Test
    @DisplayName("Con solo refresh token sirve: se puede renovar sin navegador")
    void soloConRefreshTokenSirve() throws IOException {
        Files.writeString(archivo(), "{\"refreshToken\":\"solo-refresco\"}");

        TokenSpotify token = new AlmacenTokenSpotify(archivo()).cargar().orElseThrow();

        assertFalse(token.vigente());
        assertTrue(token.puedeRenovarse());
    }

    @Test
    @DisplayName("Guardar dos veces deja el último y no ensucia con temporales")
    void guardadosSucesivos() throws IOException {
        AlmacenTokenSpotify almacen = new AlmacenTokenSpotify(archivo());

        almacen.guardar(new TokenSpotify("primero", "r1", 1));
        almacen.guardar(new TokenSpotify("segundo", "r2", 2));

        assertEquals("segundo", almacen.cargar().orElseThrow().accessToken());
        try (var entradas = Files.list(carpeta)) {
            assertEquals(1, entradas.count(), "no debe quedar ningún .tmp huérfano");
        }
    }

    @Test
    @DisplayName("Borrar deja el almacén sin token")
    void borrar() {
        AlmacenTokenSpotify almacen = new AlmacenTokenSpotify(archivo());
        almacen.guardar(new TokenSpotify("acceso", "refresco", 1));

        assertTrue(almacen.borrar());
        assertTrue(almacen.cargar().isEmpty());
    }

    @Test
    @DisplayName("Borrar algo que no existe no es un fallo")
    void borrarLoInexistente() {
        assertTrue(new AlmacenTokenSpotify(archivo()).borrar());
    }

    @Test
    @DisplayName("Guardar null no escribe nada")
    void guardarNullNoEscribe() {
        AlmacenTokenSpotify almacen = new AlmacenTokenSpotify(archivo());

        assertFalse(almacen.guardar(null));
        assertFalse(Files.exists(archivo()));
    }

    @Test
    @DisplayName("El archivo guardado no es legible como texto plano por accidente")
    void elArchivoEsJsonYNoOtraCosa() throws IOException {
        AlmacenTokenSpotify almacen = new AlmacenTokenSpotify(archivo());
        almacen.guardar(new TokenSpotify("acceso", "refresco", 123));

        String contenido = Files.readString(archivo());

        assertTrue(contenido.startsWith("{") && contenido.endsWith("}"));
        Optional<TokenSpotify> releido = almacen.cargar();
        assertTrue(releido.isPresent());
    }
}
