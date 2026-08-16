package com.eia.reproductor.servicios.spotify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del servidor que recibe el retorno del navegador. */
class ServidorDeRetornoTest {
    private static final Duration ESPERA_CORTA = Duration.ofSeconds(5);
    private static final String ESTADO = "estado-de-esta-sesion";

    /** @return un puerto libre, para que dos pruebas en paralelo no choquen */
    private static int puertoLibre() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static HttpResponse<String> llamar(int puerto, String consulta) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + puerto + "/callback?" + consulta)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("Entrega el código cuando la respuesta es correcta")
    void entregaElCodigo() throws Exception {
        int puerto = puertoLibre();
        try (ServidorDeRetorno servidor = new ServidorDeRetorno(puerto, "/callback", ESTADO)) {
            CompletableFuture<Optional<String>> esperando =
                    CompletableFuture.supplyAsync(() -> servidor.esperarCodigo(ESPERA_CORTA));

            HttpResponse<String> respuesta = llamar(puerto, "code=el-codigo&state=" + ESTADO);

            assertEquals(200, respuesta.statusCode());
            assertTrue(respuesta.body().contains("CAMELLOS VS ENANOS"));
            assertEquals(Optional.of("el-codigo"), esperando.get());
        }
    }

    @Test
    @DisplayName("Rechaza una respuesta con un state que no es el de esta sesión")
    void rechazaEstadoQueNoCoincide() throws Exception {
        int puerto = puertoLibre();
        try (ServidorDeRetorno servidor = new ServidorDeRetorno(puerto, "/callback", ESTADO)) {
            CompletableFuture<Optional<String>> esperando =
                    CompletableFuture.supplyAsync(() -> servidor.esperarCodigo(ESPERA_CORTA));

            // Es la defensa contra que un tercero induzca al navegador a entregar otro código.
            llamar(puerto, "code=codigo-ajeno&state=estado-de-otro");

            assertTrue(esperando.get().isEmpty());
            assertTrue(servidor.motivoDelFallo().orElse("").contains("no corresponde"));
        }
    }

    @Test
    @DisplayName("Informa cuando el usuario cancela en la pantalla de Spotify")
    void informaLaCancelacion() throws Exception {
        int puerto = puertoLibre();
        try (ServidorDeRetorno servidor = new ServidorDeRetorno(puerto, "/callback", ESTADO)) {
            CompletableFuture<Optional<String>> esperando =
                    CompletableFuture.supplyAsync(() -> servidor.esperarCodigo(ESPERA_CORTA));

            llamar(puerto, "error=access_denied&state=" + ESTADO);

            assertTrue(esperando.get().isEmpty());
            assertTrue(servidor.motivoDelFallo().orElse("").contains("access_denied"));
        }
    }

    @Test
    @DisplayName("Informa si Spotify no devuelve código")
    void informaLaRespuestaIncompleta() throws Exception {
        int puerto = puertoLibre();
        try (ServidorDeRetorno servidor = new ServidorDeRetorno(puerto, "/callback", ESTADO)) {
            CompletableFuture<Optional<String>> esperando =
                    CompletableFuture.supplyAsync(() -> servidor.esperarCodigo(ESPERA_CORTA));

            llamar(puerto, "state=" + ESTADO);

            assertTrue(esperando.get().isEmpty());
            assertTrue(servidor.motivoDelFallo().orElse("").contains("código"));
        }
    }

    @Test
    @DisplayName("Se rinde si el usuario nunca autoriza")
    void seRindeAlAgotarseLaEspera() throws Exception {
        int puerto = puertoLibre();
        try (ServidorDeRetorno servidor = new ServidorDeRetorno(puerto, "/callback", ESTADO)) {
            Optional<String> codigo = servidor.esperarCodigo(Duration.ofMillis(200));

            assertTrue(codigo.isEmpty());
            assertTrue(servidor.motivoDelFallo().orElse("").contains("tiempo"));
        }
    }

    @Test
    @DisplayName("Al cerrarse suelta el puerto: no queda escuchando de más")
    void alCerrarseSueltaElPuerto() throws Exception {
        int puerto = puertoLibre();
        try (ServidorDeRetorno servidor = new ServidorDeRetorno(puerto, "/callback", ESTADO)) {
            assertEquals(200, llamar(puerto, "code=c&state=" + ESTADO).statusCode());
            servidor.esperarCodigo(ESPERA_CORTA);
        }

        // El servidor vive lo minimo posible: apenas recibe el codigo, el puerto queda libre.
        assertThrows(ConnectException.class, () -> llamar(puerto, "code=otro"));
    }

    @Test
    @DisplayName("Dos peticiones seguidas no pisan el código ya recibido")
    void unaSegundaPeticionNoPisaElCodigo() throws Exception {
        int puerto = puertoLibre();
        try (ServidorDeRetorno servidor = new ServidorDeRetorno(puerto, "/callback", ESTADO)) {
            llamar(puerto, "code=el-bueno&state=" + ESTADO);
            assertEquals(Optional.of("el-bueno"), servidor.esperarCodigo(ESPERA_CORTA));

            // Un refresco del navegador sobre la misma URL no debe alterar lo ya entregado.
            llamar(puerto, "error=access_denied&state=" + ESTADO);

            assertEquals(Optional.of("el-bueno"), servidor.esperarCodigo(ESPERA_CORTA));
        }
    }
}
