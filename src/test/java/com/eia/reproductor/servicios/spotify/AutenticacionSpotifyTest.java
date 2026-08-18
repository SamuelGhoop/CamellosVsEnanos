package com.eia.reproductor.servicios.spotify;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * Pruebas de cuando se tira la sesion guardada y cuando no.
     *
     * <p><b>Por que existen.</b> {@code renovar()} borraba el token ante cualquier respuesta
     * vacia, y {@code pedirToken()} devuelve vacio tanto si Spotify repudia la credencial como si
     * no hubo forma de preguntarle. Resultado: un corte de red de un segundo, o cerrar la
     * aplicacion a media renovacion, borraba la sesion y obligaba a autorizar en el navegador otra
     * vez. Le paso al usuario en mitad de una sesion de trabajo: la aplicacion volvio al audio
     * simulado sin decir nada, porque sin token ni siquiera se lanza librespot.</p>
     */
    @Nested
    @DisplayName("Al renovar el token")
    class AlRenovar {

        /** Levanta un servidor local que siempre responde lo mismo. */
        private HttpServer servidorQueResponde(int codigo, String cuerpo) throws IOException {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/token", intercambio -> {
                byte[] datos = cuerpo.getBytes(StandardCharsets.UTF_8);
                intercambio.sendResponseHeaders(codigo, datos.length);
                try (var salida = intercambio.getResponseBody()) {
                    salida.write(datos);
                }
            });
            servidor.start();
            return servidor;
        }

        /** Deja un token caducado pero renovable, que es lo que dispara renovar(). */
        private Path archivoConTokenRenovable() throws IOException {
            Path archivo = Files.createTempFile("token", ".json");
            archivo.toFile().deleteOnExit();
            new AlmacenTokenSpotify(archivo).guardar(
                    new TokenSpotify("acceso-viejo", "refresco", System.currentTimeMillis() - 1));
            return archivo;
        }

        private ConfiguracionSpotify configuracion() throws IOException {
            Path archivo = Files.createTempFile("spotify", ".properties");
            Files.writeString(archivo, """
                    client.id=abc123
                    redirect.uri=http://127.0.0.1:8888/callback
                    device.name=Camellos vs Enanos
                    """);
            archivo.toFile().deleteOnExit();
            return ConfiguracionSpotify.cargar(archivo).orElseThrow();
        }

        private boolean renovarContra(HttpServer servidor, Path tokenGuardado) throws IOException {
            String url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/token";
            return new AutenticacionSpotify(
                    configuracion(), new AlmacenTokenSpotify(tokenGuardado), url)
                    .renovarParaPruebas();
        }

        @Test
        @DisplayName("Un 400 sí tira la sesión: esa credencial ya no sirve")
        void elRechazoTiraLaSesion() throws IOException {
            Path guardado = archivoConTokenRenovable();
            HttpServer servidor = servidorQueResponde(400,
                    "{\"error\":\"invalid_grant\",\"error_description\":\"Refresh token revoked\"}");

            try {
                assertFalse(renovarContra(servidor, guardado));
                assertFalse(Files.exists(guardado),
                        "un refresh token revocado no se recupera: hay que autorizar de nuevo");
            } finally {
                servidor.stop(0);
            }
        }

        @Test
        @DisplayName("Un 503 NO tira la sesión: Spotify tuvo un mal rato, la credencial sirve")
        void elFalloPasajeroConservaLaSesion() throws IOException {
            Path guardado = archivoConTokenRenovable();
            HttpServer servidor = servidorQueResponde(503, "{\"error\":\"server_error\"}");

            try {
                assertFalse(renovarContra(servidor, guardado), "la renovación no salió");
                assertTrue(Files.exists(guardado),
                        "se borró la sesión por una caída pasajera: al reabrir pediría el navegador");
            } finally {
                servidor.stop(0);
            }
        }

        @Test
        @DisplayName("Sin poder contactar a Spotify tampoco se tira la sesión")
        void sinRedConservaLaSesion() throws IOException {
            Path guardado = archivoConTokenRenovable();
            // Se levanta y se apaga: el puerto queda muerto, que es lo mismo que estar sin red.
            HttpServer servidor = servidorQueResponde(200, "{}");
            int puerto = servidor.getAddress().getPort();
            servidor.stop(0);

            String url = "http://127.0.0.1:" + puerto + "/token";
            boolean renovado = new AutenticacionSpotify(
                    configuracion(), new AlmacenTokenSpotify(guardado), url).renovarParaPruebas();

            assertFalse(renovado);
            assertTrue(Files.exists(guardado),
                    "sin conexión el token no se toca: sigue sirviendo cuando vuelva la red");
        }
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
