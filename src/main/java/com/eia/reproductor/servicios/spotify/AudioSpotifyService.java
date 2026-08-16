package com.eia.reproductor.servicios.spotify;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.servicios.ReproductorAudio;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.SimpleBooleanProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Fuente de audio que reproduce por Spotify, usando librespot como dispositivo. */
public class AudioSpotifyService implements ReproductorAudio {
    /** Cada cuanto se le pregunta a Spotify por la posicion. */
    private static final long INTERVALO_SONDEO_MS = 1_000;

    /** Cuanto se espera a que el usuario suelte la barra antes de mandar el volumen. */
    private static final long ESPERA_AGRUPAR_VOLUMEN_MS = 200;

    /** Cuanto se desoye al sondeo tras pausar o reanudar, mientras la API se pone al dia. */
    private static final long VENTANA_TRAS_ORDEN_MS = 1_500;

    /** Cuanto se espera a que Spotify confirme un salto antes de rendirse. */
    private static final long VENTANA_TRAS_SALTO_MS = 5_000;

    /** Margen para dar un salto por aterrizado; el sondeo nunca cae en el milisegundo exacto. */
    private static final long TOLERANCIA_SALTO_MS = 3_000;

    /** Respuesta de Spotify cuando el dispositivo se durmio y ya no acepta ordenes. */
    private static final int HTTP_SIN_DISPOSITIVO = 404;

    /** Donde librespot guarda sus credenciales; sin esto pediria autorizar en el navegador. */
    private static final Path CREDENCIALES_LIBRESPOT =
            Path.of("data", "librespot-cache", "credentials.json");

    private final ConfiguracionSpotify configuracion;
    private final AutenticacionSpotify autenticacion;
    private final ClienteWebApiSpotify api;
    private final ProcesoLibrespot librespot;
    private final DetectorFinDePista detector = new DetectorFinDePista();

    private final ReadOnlyLongWrapper posicionMs = new ReadOnlyLongWrapper(0);
    private final ReadOnlyLongWrapper duracionMs = new ReadOnlyLongWrapper(0);
    private final BooleanProperty reproduciendo = new SimpleBooleanProperty(false);

    /** Hilo unico por el que pasan todas las ordenes a Spotify. */
    private final ScheduledExecutorService ordenes = Executors.newSingleThreadScheduledExecutor(
            tarea -> {
                Thread hilo = new Thread(tarea, "ordenes-spotify");
                hilo.setDaemon(true);
                return hilo;
            });

    /** Ultimo volumen pedido; solo se manda este, no todos los intermedios. */
    private final AtomicInteger volumenPedido = new AtomicInteger(100);

    /** Envio de volumen pendiente, para cancelarlo si llega otro antes. */
    private ScheduledFuture<?> envioDeVolumen;

    /** Hasta cuando ignorar lo que diga el sondeo sobre si esta sonando. */
    private volatile long ignorarEstadoHasta;

    /** Posicion a la que se acaba de saltar, o -1 si no hay salto pendiente de confirmar. */
    private volatile long objetivoDelSalto = -1;

    /** Hasta cuando se le da tiempo a Spotify para confirmar el salto. */
    private volatile long esperarSaltoHasta;

    private ScheduledExecutorService sondeo;
    private volatile boolean dispositivoListo;
    private volatile String uriEnCurso;

    /** Volumen pedido por la interfaz; se aplica en cuanto el dispositivo esta listo. */
    private volatile int volumen = 100;
    private Runnable alTerminarPista;
    private Consumer<String> alFallar;

    /** Crea la fuente si la maquina esta lista para usarla, sin molestar al usuario. */
    public static Optional<AudioSpotifyService> crearSiEstaConfigurado() {
        Optional<ConfiguracionSpotify> configuracion = ConfiguracionSpotify.cargar();
        if (configuracion.isEmpty()) {
            return Optional.empty();
        }
        AutenticacionSpotify autenticacion =
                new AutenticacionSpotify(configuracion.get(), new AlmacenTokenSpotify());
        if (!autenticacion.haySesion()) {
            return Optional.empty();
        }
        // Sin las credenciales de librespot, arrancarlo abriria el navegador de golpe. Que el
        // usuario autorice cuando el quiera, con el diagnostico, no al abrir la aplicacion.
        if (!Files.isRegularFile(CREDENCIALES_LIBRESPOT)) {
            return Optional.empty();
        }
        if (ProcesoLibrespot.localizarEjecutable().isEmpty()) {
            return Optional.empty();
        }

        ClienteWebApiSpotify api = new ClienteWebApiSpotify(autenticacion);
        AudioSpotifyService servicio = new AudioSpotifyService(
                configuracion.get(), autenticacion, api,
                new ProcesoLibrespot(configuracion.get(), api));
        servicio.prepararEnSegundoPlano();
        return Optional.of(servicio);
    }

    /** Construye la fuente con sus colaboradores. */
    public AudioSpotifyService(ConfiguracionSpotify configuracion,
                               AutenticacionSpotify autenticacion,
                               ClienteWebApiSpotify api,
                               ProcesoLibrespot librespot) {
        this.configuracion = configuracion;
        this.autenticacion = autenticacion;
        this.api = api;
        this.librespot = librespot;
    }

    // --- Preparacion ---

    /** Lanza librespot y le transfiere la reproduccion, sin bloquear a quien llama. */
    public void prepararEnSegundoPlano() {
        Thread arranque = new Thread(this::preparar, "arrancar-spotify");
        arranque.setDaemon(true);
        arranque.start();
    }

    /** Deja el dispositivo listo para recibir ordenes. */
    private void preparar() {
        if (!librespot.iniciar()) {
            avisar(librespot.ultimoAviso().orElse("No se pudo iniciar Spotify."));
            return;
        }
        Optional<DispositivoSpotify> dispositivo = librespot.dispositivo();
        if (dispositivo.isEmpty()) {
            avisar("librespot arrancó pero Spotify no lo reconoció.");
            return;
        }
        String id = dispositivo.get().id();
        if (!api.transferirA(id)) {
            avisar(api.ultimoAviso().orElse("No se pudo transferir la reproducción a Spotify."));
            return;
        }
        // La cuenta puede traer repeticion o aleatorio de otra sesion. Si se dejan, Spotify
        // decide el orden en vez de las estructuras de datos.
        api.silenciarRepeticionYAleatorio(id);
        // El volumen guardado en la cache de librespot le gana al de arranque, asi que se fija
        // aqui tambien; si no, se oye a la mitad y en curva logaritmica, o sea muy bajo.
        api.ajustarVolumen(id, volumen);
        // Transferir no significa silencio: si la cuenta venia sonando, sigue sonando.
        api.pausar(id);
        dispositivoListo = true;
    }

    // --- ReproductorAudio ---

    @Override
    public void reproducir(Cancion cancion) {
        if (!puedeReproducir(cancion) || !disponible()) {
            return;
        }
        String uri = cancion.getUriSpotify();
        enSegundoPlano(() -> {
            if (!ordenarDespertando(() -> api.reproducir(idDispositivo(), uri, 0))) {
                avisar(api.ultimoAviso().orElse("Spotify no pudo reproducir la canción."));
                return;
            }
            uriEnCurso = uri;
            // Un salto pendiente de la cancion anterior bloquearia las posiciones de esta.
            objetivoDelSalto = -1;
            detector.vigilar(uri);
            enInterfaz(() -> {
                posicionMs.set(0);
                duracionMs.set(cancion.getDuracionSegundos() * 1000L);
                reproduciendo.set(true);
            });
            arrancarSondeo();
        });
    }

    /** {@inheritDoc} El boton cambia de inmediato y la peticion viaja despues. */
    @Override
    public void pausar() {
        enInterfaz(() -> reproduciendo.set(false));
        ignorarEstadoHasta = System.currentTimeMillis() + VENTANA_TRAS_ORDEN_MS;
        enSegundoPlano(() -> {
            if (!ordenarDespertando(() -> api.pausar(idDispositivo()))) {
                // Si de verdad no se pudo pausar, el boton no puede seguir diciendo que si:
                // quedaria la musica sonando y la interfaz afirmando lo contrario.
                enInterfaz(() -> reproduciendo.set(true));
                avisar("No se pudo pausar: " + api.ultimoAviso().orElse("Spotify no respondió."));
            }
        });
    }

    @Override
    public void reanudar() {
        enInterfaz(() -> reproduciendo.set(true));
        ignorarEstadoHasta = System.currentTimeMillis() + VENTANA_TRAS_ORDEN_MS;
        enSegundoPlano(() -> {
            if (!ordenarDespertando(() -> api.reanudar(idDispositivo()))) {
                enInterfaz(() -> reproduciendo.set(false));
                avisar("No se pudo reanudar: " + api.ultimoAviso().orElse("Spotify no respondió."));
            }
        });
    }

    @Override
    public void detener() {
        detector.olvidar();
        uriEnCurso = null;
        objetivoDelSalto = -1;
        detenerSondeo();
        enSegundoPlano(() -> api.pausar(idDispositivo()));
        enInterfaz(() -> {
            reproduciendo.set(false);
            posicionMs.set(0);
        });
    }

    /** {@inheritDoc} La barra salta al instante y la peticion viaja despues. */
    @Override
    public void buscarPosicion(long milisegundos) {
        long limitada = Math.max(0, milisegundos);
        objetivoDelSalto = limitada;
        esperarSaltoHasta = System.currentTimeMillis() + VENTANA_TRAS_SALTO_MS;
        enInterfaz(() -> posicionMs.set(limitada));
        enSegundoPlano(() -> ordenarDespertando(() -> api.buscarPosicion(idDispositivo(), limitada)));
    }

    /** Decide si hay que creerle la posicion a un sondeo. */
    private boolean posicionEsCreible(long posicionReportada) {
        if (objetivoDelSalto < 0) {
            return true;
        }
        if (Math.abs(posicionReportada - objetivoDelSalto) <= TOLERANCIA_SALTO_MS) {
            // Aterrizo donde se pidio: a partir de aqui la API vuelve a ser de fiar.
            objetivoDelSalto = -1;
            return true;
        }
        if (System.currentTimeMillis() >= esperarSaltoHasta) {
            // Se acabo la paciencia: el salto no llego a ninguna parte y es mejor mostrar la
            // verdad que dejar la barra congelada en un destino que nunca ocurrio.
            objetivoDelSalto = -1;
            return true;
        }
        return false;
    }

    @Override
    public void avanzarRelativo(long milisegundos) {
        buscarPosicion(posicionMs.get() + milisegundos);
    }

    @Override
    public ReadOnlyLongProperty posicionMsProperty() {
        return posicionMs.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyLongProperty duracionMsProperty() {
        return duracionMs.getReadOnlyProperty();
    }

    @Override
    public BooleanProperty reproduciendoProperty() {
        return reproduciendo;
    }

    /**
     * {@inheritDoc} Refleja el estado vivo, no la configuracion: hacen falta las tres cosas a la
     * vez —proceso arriba, sesion valida y dispositivo ya transferido—.
     */
    @Override
    public boolean disponible() {
        return dispositivoListo && librespot.activo() && autenticacion.haySesion();
    }

    @Override
    public boolean puedeReproducir(Cancion cancion) {
        return cancion != null && cancion.tieneUriSpotify();
    }

    @Override
    public String nombreFuente() {
        return "Spotify";
    }

    @Override
    public void setAlTerminarPista(Runnable callback) {
        this.alTerminarPista = callback;
    }

    @Override
    public void setAlFallar(Consumer<String> callback) {
        this.alFallar = callback;
    }

    /** {@inheritDoc} Sin conexion esta fuente no sirve para nada. */
    @Override
    public boolean requiereRed() {
        return true;
    }

    /**
     * {@inheritDoc} Va por la Web API y por tanto por la red, asi que se manda en segundo plano:
     * arrastrar la barra de volumen no puede congelar la interfaz mientras viaja la peticion.
     */
    /**
     * {@inheritDoc} Los cambios se agrupan: arrastrar la barra genera decenas de valores por
     * segundo y solo interesa el ultimo.
     */
    @Override
    public synchronized void setVolumen(int porcentaje) {
        int limitado = Math.max(0, Math.min(100, porcentaje));
        volumen = limitado;
        volumenPedido.set(limitado);
        if (!dispositivoListo) {
            // Todavia no hay a quien pedirselo; se aplicara al terminar de preparar.
            return;
        }
        if (envioDeVolumen != null) {
            envioDeVolumen.cancel(false);
        }
        envioDeVolumen = ordenes.schedule(
                () -> api.ajustarVolumen(idDispositivo(), volumenPedido.get()),
                ESPERA_AGRUPAR_VOLUMEN_MS, TimeUnit.MILLISECONDS);
    }

    /** Apaga el sondeo, la cola de ordenes y librespot. */
    public void cerrar() {
        detenerSondeo();
        ordenes.shutdownNow();
        librespot.detener();
        dispositivoListo = false;
    }

    // --- Sondeo ---

    private synchronized void arrancarSondeo() {
        if (sondeo != null) {
            return;
        }
        sondeo = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "sondeo-spotify");
            // Demonio: si la aplicacion se cierra, este hilo no debe impedir que la maquina
            // virtual termine.
            hilo.setDaemon(true);
            return hilo;
        });
        sondeo.scheduleWithFixedDelay(this::sondear,
                INTERVALO_SONDEO_MS, INTERVALO_SONDEO_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void detenerSondeo() {
        if (sondeo != null) {
            sondeo.shutdownNow();
            sondeo = null;
        }
    }

    /** Una vuelta del sondeo: lee el estado, refresca la interfaz y comprueba el fin de pista. */
    private void sondear() {
        try {
            EstadoReproductorSpotify estado = api.estadoDelReproductor().orElse(null);
            if (estado != null && esDeLaPistaEnCurso(estado)) {
                // Justo despues de pausar o reanudar, la API todavia contesta el estado anterior.
                // Hacerle caso revertiria el boton y pareceria que la orden no funciono.
                boolean confiarEnElEstado = System.currentTimeMillis() >= ignorarEstadoHasta;
                boolean confiarEnLaPosicion = posicionEsCreible(estado.posicionMs());
                enInterfaz(() -> {
                    if (confiarEnLaPosicion) {
                        posicionMs.set(estado.posicionMs());
                    }
                    if (estado.duracionMs() > 0) {
                        duracionMs.set(estado.duracionMs());
                    }
                    if (confiarEnElEstado) {
                        reproduciendo.set(estado.reproduciendo());
                    }
                });
            }
            if (detector.observar(estado)) {
                detenerSondeo();
                enInterfaz(() -> reproduciendo.set(false));
                if (alTerminarPista != null) {
                    // Quien decide la siguiente cancion es el modo activo, o sea la estructura
                    // de datos. Spotify solo avisa que esta termino.
                    enInterfaz(alTerminarPista);
                }
            }
        } catch (RuntimeException falloDeUnaVuelta) {
            avisar("Se perdió el contacto con Spotify: " + falloDeUnaVuelta.getMessage());
        }
    }

    private boolean esDeLaPistaEnCurso(EstadoReproductorSpotify estado) {
        return uriEnCurso == null || uriEnCurso.equals(estado.uriPista());
    }

    private String idDispositivo() {
        return librespot.dispositivo().map(DispositivoSpotify::id).orElse(null);
    }

    private void avisar(String mensaje) {
        if (alFallar != null) {
            enInterfaz(() -> alFallar.accept(mensaje));
        }
    }

    // --- Hilos ---

    /** Lanza una tarea de red fuera del hilo de la interfaz, que no debe bloquearse jamas. */
    private void enSegundoPlano(Runnable tarea) {
        ordenes.execute(tarea);
    }

    /** Ejecuta una orden del reproductor, despertando el dispositivo si se habia dormido. */
    private boolean ordenarDespertando(BooleanSupplier orden) {
        if (orden.getAsBoolean()) {
            return true;
        }
        if (api.ultimoEstado() != HTTP_SIN_DISPOSITIVO) {
            return false;
        }
        String id = idDispositivo();
        if (id == null || !api.transferirA(id)) {
            return false;
        }
        return orden.getAsBoolean();
    }

    /** Ejecuta en el hilo de la interfaz lo que toque las propiedades observables. */
    private static void enInterfaz(Runnable tarea) {
        try {
            if (Platform.isFxApplicationThread()) {
                tarea.run();
            } else {
                Platform.runLater(tarea);
            }
        } catch (IllegalStateException sinEntornoGrafico) {
            tarea.run();
        }
    }
}
