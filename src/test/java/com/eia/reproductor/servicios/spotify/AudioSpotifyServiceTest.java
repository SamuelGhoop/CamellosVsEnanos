package com.eia.reproductor.servicios.spotify;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.servicios.ReproductorAudio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del contrato de la fuente de Spotify. */
class AudioSpotifyServiceTest {
    /** Proceso de mentira: permite fijar si esta vivo y con que dispositivo, sin lanzar nada. */
    private static final class LibrespotFalso extends ProcesoLibrespot {
        private boolean vivo;
        private DispositivoSpotify dispositivo;

        LibrespotFalso(ConfiguracionSpotify configuracion) {
            super(configuracion, null);
        }

        /** Se da por arrancado sin lanzar el proceso: "mvn test" no puede abrir librespot. */
        @Override public synchronized boolean iniciar() {
            return vivo;
        }

        @Override public boolean activo() {
            return vivo;
        }

        @Override public Optional<DispositivoSpotify> dispositivo() {
            return Optional.ofNullable(dispositivo);
        }
    }

    /** Cliente de mentira: responde sin tocar la red y anota que se le pidio. */
    private static final class ClienteFalso extends ClienteWebApiSpotify {
        private final AutenticacionSpotify autenticacion;
        private boolean transferenciaFunciona;
        private int transferencias;
        private int pausas;
        private int ajustesDeVolumen;

        ClienteFalso(AutenticacionSpotify autenticacion) {
            super(autenticacion);
            this.autenticacion = autenticacion;
        }

        @Override public boolean transferirA(String idDispositivo) {
            transferencias++;
            return transferenciaFunciona;
        }

        @Override public boolean silenciarRepeticionYAleatorio(String idDispositivo) {
            return true;
        }

        @Override public boolean ajustarVolumen(String idDispositivo, int porcentaje) {
            ajustesDeVolumen++;
            return true;
        }

        @Override public boolean pausar(String idDispositivo) {
            pausas++;
            return true;
        }
    }

    private static ConfiguracionSpotify configuracion() {
        // Se apoya en la carga real desde un archivo para no duplicar el formato.
        return ConfiguracionSpotify.cargar(escribirConfiguracion()).orElseThrow();
    }

    private static Path escribirConfiguracion() {
        try {
            Path archivo = java.nio.file.Files.createTempFile("spotify", ".properties");
            java.nio.file.Files.writeString(archivo, """
                    client.id=abc123
                    redirect.uri=http://127.0.0.1:8888/callback
                    device.name=Camellos vs Enanos
                    """);
            archivo.toFile().deleteOnExit();
            return archivo;
        } catch (java.io.IOException fallo) {
            throw new IllegalStateException(fallo);
        }
    }

    /** Autenticacion de mentira: dice que hay sesion sin token ni red. */
    private static final class AutenticacionFalsa extends AutenticacionSpotify {
        AutenticacionFalsa(ConfiguracionSpotify configuracion) {
            super(configuracion, new AlmacenTokenSpotify(Path.of("no-existe-a-proposito.json")));
        }

        @Override public boolean haySesion() {
            return true;
        }
    }

    private static AudioSpotifyService servicioCon(LibrespotFalso librespot) {
        ConfiguracionSpotify config = configuracion();
        AutenticacionSpotify autenticacion = new AutenticacionSpotify(
                config, new AlmacenTokenSpotify(Path.of("no-existe-a-proposito.json")));
        return new AudioSpotifyService(
                config, autenticacion, new ClienteWebApiSpotify(autenticacion), librespot);
    }

    /** Monta el servicio con todos los colaboradores falsos, listo para ejercitar preparar(). */
    private static AudioSpotifyService servicioCon(LibrespotFalso librespot, ClienteFalso cliente) {
        return new AudioSpotifyService(configuracion(), cliente.autenticacion, cliente, librespot);
    }

    /** Deja un librespot vivo y con dispositivo, que es el punto de partida de preparar(). */
    private static LibrespotFalso librespotListo() {
        LibrespotFalso librespot = new LibrespotFalso(configuracion());
        librespot.vivo = true;
        librespot.dispositivo =
                new DispositivoSpotify("id-libre", "Camellos vs Enanos", false, "Computer");
        return librespot;
    }

    private static Cancion conUri(String uri) {
        Cancion cancion = new Cancion("Una canción");
        cancion.setUriSpotify(uri);
        return cancion;
    }

    @Test
    @DisplayName("Sabe reproducir solo las canciones con URI de Spotify")
    void soloLasQueTienenUri() {
        AudioSpotifyService servicio = servicioCon(new LibrespotFalso(configuracion()));

        assertTrue(servicio.puedeReproducir(conUri("spotify:track:abc")));
        assertFalse(servicio.puedeReproducir(new Cancion("Solo metadata")));
        assertFalse(servicio.puedeReproducir(conUri("   ")));
        assertFalse(servicio.puedeReproducir(null));
    }

    @Test
    @DisplayName("No está disponible mientras el dispositivo no esté listo")
    void noDisponibleSinDispositivoListo() {
        LibrespotFalso librespot = new LibrespotFalso(configuracion());
        librespot.vivo = true;

        // Arrancar librespot no basta: falta que preparar() deje el dispositivo listo.
        assertFalse(servicioCon(librespot).disponible());
    }

    @Test
    @DisplayName("Queda disponible aunque la transferencia falle")
    void disponibleSinTransferencia() {
        // El caso real que dejo la aplicacion muda: abrirla sin ninguna sesion de Spotify abierta.
        // Spotify devuelve 500 al transferir —no hay contexto que mover— y antes preparar() se
        // rendia ahi, dejando el audio en el simulado hasta reiniciar. Reproducir con device_id
        // activa el dispositivo igual, asi que la transferencia no puede ser un requisito.
        ClienteFalso cliente = new ClienteFalso(new AutenticacionFalsa(configuracion()));
        cliente.transferenciaFunciona = false;
        AudioSpotifyService servicio = servicioCon(librespotListo(), cliente);

        servicio.preparar();

        assertTrue(servicio.disponible(),
                "sin transferencia el audio se iría al simulado y no sonaría nada");
        assertEquals(1, cliente.transferencias, "se intenta transferir igual, por si funciona");
        assertEquals(0, cliente.pausas,
                "sin transferencia no hay nada sonando en el dispositivo que haya que pausar");
    }

    @Test
    @DisplayName("Con la transferencia buena, pausa lo que la cuenta viniera sonando")
    void conTransferenciaPausa() {
        ClienteFalso cliente = new ClienteFalso(new AutenticacionFalsa(configuracion()));
        cliente.transferenciaFunciona = true;
        AudioSpotifyService servicio = servicioCon(librespotListo(), cliente);

        servicio.preparar();

        assertTrue(servicio.disponible());
        assertEquals(1, cliente.pausas, "transferir no silencia: la cuenta seguiría sonando");
    }

    @Test
    @DisplayName("Sin librespot arriba no queda listo, transferencia aparte")
    void sinProcesoNoQuedaListo() {
        // El proceso caido es un fallo de verdad y sigue abortando: no hay dispositivo al que
        // mandarle nada.
        ClienteFalso cliente = new ClienteFalso(new AutenticacionFalsa(configuracion()));
        cliente.transferenciaFunciona = true;
        LibrespotFalso muerto = new LibrespotFalso(configuracion());
        muerto.vivo = false;
        AudioSpotifyService servicio = servicioCon(muerto, cliente);

        servicio.preparar();

        assertFalse(servicio.disponible());
        assertEquals(0, cliente.transferencias, "ni se intenta: no hay a quien");
    }

    @Test
    @DisplayName("No está disponible si el proceso se murió")
    void noDisponibleSinProceso() {
        LibrespotFalso librespot = new LibrespotFalso(configuracion());
        librespot.vivo = false;

        assertFalse(servicioCon(librespot).disponible());
    }

    @Test
    @DisplayName("Sin sesión guardada no está disponible aunque el proceso viva")
    void noDisponibleSinSesion() {
        LibrespotFalso librespot = new LibrespotFalso(configuracion());
        librespot.vivo = true;
        librespot.dispositivo = new DispositivoSpotify("id", "Camellos vs Enanos", true, "Speaker");

        // El almacen apunta a un archivo inexistente: no hay token ni forma de renovarlo.
        assertFalse(servicioCon(librespot).disponible());
    }

    @Test
    @DisplayName("Declara que necesita red, para que el interruptor FUENTE la descarte")
    void declaraQueNecesitaRed() {
        assertTrue(servicioCon(new LibrespotFalso(configuracion())).requiereRed());
    }

    @Test
    @DisplayName("Se identifica con un nombre para la interfaz")
    void seIdentifica() {
        assertEquals("Spotify", servicioCon(new LibrespotFalso(configuracion())).nombreFuente());
    }

    @Test
    @DisplayName("Cumple el contrato de ReproductorAudio")
    void cumpleElContrato() {
        ReproductorAudio comoInterfaz = servicioCon(new LibrespotFalso(configuracion()));

        // El controlador solo ve este tipo: si esto compila y responde, el polimorfismo se sostiene.
        assertEquals(0, comoInterfaz.posicionMsProperty().get());
        assertEquals(0, comoInterfaz.duracionMsProperty().get());
        assertFalse(comoInterfaz.reproduciendoProperty().get());
    }

    @Test
    @DisplayName("Las órdenes con la fuente no disponible no revientan")
    void lasOrdenesSonInocuasSinDispositivo() {
        AudioSpotifyService servicio = servicioCon(new LibrespotFalso(configuracion()));

        servicio.reproducir(conUri("spotify:track:abc"));
        servicio.pausar();
        servicio.detener();

        assertFalse(servicio.reproduciendoProperty().get());
    }

    // Nota: crearSiEstaConfigurado() no se prueba aqui a proposito. En esta maquina todos sus
    // requisitos se cumplen, asi que llamarlo lanzaria librespot de verdad en mitad de "mvn test".
}
