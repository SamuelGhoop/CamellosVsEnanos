package com.eia.reproductor.servicios.spotify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de la parte criptografica de PKCE. */
class AutenticacionSpotifyTest {
    /** Caracteres que RFC 7636 permite en el verificador. */
    private static final Pattern PERMITIDOS = Pattern.compile("[A-Za-z0-9\\-._~]+");

    @Test
    @DisplayName("El verificador cumple el largo que exige la especificación")
    void verificadorConLargoValido() {
        String verificador = AutenticacionSpotify.generarVerificador();

        // RFC 7636: entre 43 y 128 caracteres.
        assertTrue(verificador.length() >= 43, "muy corto: " + verificador.length());
        assertTrue(verificador.length() <= 128, "muy largo: " + verificador.length());
    }

    @Test
    @DisplayName("El verificador usa solo caracteres seguros en una URL")
    void verificadorConCaracteresSeguros() {
        String verificador = AutenticacionSpotify.generarVerificador();

        assertTrue(PERMITIDOS.matcher(verificador).matches(),
                "tiene caracteres que habría que escapar: " + verificador);
    }

    @Test
    @DisplayName("Cada verificador es distinto")
    void cadaVerificadorEsDistinto() {
        // Si se repitieran, un código interceptado de una sesión serviría para otra.
        assertNotEquals(AutenticacionSpotify.generarVerificador(),
                AutenticacionSpotify.generarVerificador());
    }

    @Test
    @DisplayName("El desafío es el SHA-256 del verificador en base64url sin relleno")
    void desafioEsElSha256DelVerificador() throws Exception {
        String verificador = AutenticacionSpotify.generarVerificador();

        String desafio = AutenticacionSpotify.calcularDesafio(verificador);

        byte[] esperado = MessageDigest.getInstance("SHA-256")
                .digest(verificador.getBytes(StandardCharsets.US_ASCII));
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(esperado), desafio);
    }

    @Test
    @DisplayName("El desafío coincide con el ejemplo oficial del RFC 7636")
    void desafioCoincideConElRfc() {
        // Vector de prueba del apéndice B del RFC: si esto pasa, la implementación es correcta
        // aunque el servidor de Spotify esté caído.
        String verificador = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                AutenticacionSpotify.calcularDesafio(verificador));
    }

    @Test
    @DisplayName("El desafío no revela el verificador")
    void desafioNoEsElVerificador() {
        String verificador = AutenticacionSpotify.generarVerificador();

        assertNotEquals(verificador, AutenticacionSpotify.calcularDesafio(verificador));
    }

    @Test
    @DisplayName("Los parámetros se codifican como formulario")
    void codificaLosParametros() {
        Map<String, String> parametros = new LinkedHashMap<>();
        parametros.put("grant_type", "authorization_code");
        parametros.put("redirect_uri", "http://127.0.0.1:8888/callback");

        String cuerpo = AutenticacionSpotify.comoFormulario(parametros);

        assertEquals("grant_type=authorization_code"
                + "&redirect_uri=http%3A%2F%2F127.0.0.1%3A8888%2Fcallback", cuerpo);
    }

    @Test
    @DisplayName("Los permisos pedidos son los mínimos para controlar la reproducción")
    void permisosMinimos() {
        String[] permisos = AutenticacionSpotify.PERMISOS.split(" ");

        // Cuantos menos permisos, menos puede hacer el token si alguna vez se filtra.
        assertEquals(2, permisos.length, "se están pidiendo permisos de más: "
                + AutenticacionSpotify.PERMISOS);
        assertTrue(AutenticacionSpotify.PERMISOS.contains("user-read-playback-state"));
        assertTrue(AutenticacionSpotify.PERMISOS.contains("user-modify-playback-state"));
    }
}
