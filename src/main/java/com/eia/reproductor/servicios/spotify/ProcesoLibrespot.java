package com.eia.reproductor.servicios.spotify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Lanza y vigila el proceso externo de librespot. */
public class ProcesoLibrespot implements AutoCloseable {
    /** Ubicacion del registro de librespot, relativa a la carpeta de ejecucion. */
    public static final Path RUTA_LOG = Path.of("logs", "librespot.log");

    /** Carpeta de cache de librespot; ahi guarda sus credenciales y el audio. */
    private static final Path RUTA_CACHE = Path.of("data", "librespot-cache");

    /** Donde se anota el identificador del proceso lanzado. */
    private static final Path RUTA_PID = RUTA_CACHE.resolve("librespot.pid");

    /** Cuanto se espera a que Spotify reconozca el dispositivo antes de rendirse. */
    private static final Duration ESPERA_REGISTRO = Duration.ofSeconds(45);

    /** Cada cuanto se le pregunta a Spotify si ya ve el dispositivo. */
    private static final Duration INTERVALO_SONDEO = Duration.ofSeconds(2);

    /** Margen para que librespot cierre por las buenas antes de matarlo. */
    private static final Duration ESPERA_CIERRE_ORDENADO = Duration.ofSeconds(5);

    private final ConfiguracionSpotify configuracion;
    private final ClienteWebApiSpotify api;

    private Process proceso;
    private Thread ganchoDeCierre;
    private DispositivoSpotify dispositivo;
    private String ultimoAviso;

    /** Crea el vigilante del proceso. */
    public ProcesoLibrespot(ConfiguracionSpotify configuracion, ClienteWebApiSpotify api) {
        this.configuracion = configuracion;
        this.api = api;
    }

    /** Arranca librespot y espera a que Spotify lo reconozca. */
    public synchronized boolean iniciar() {
        if (activo()) {
            return true;
        }
        Optional<Path> ejecutable = localizarEjecutable();
        if (ejecutable.isEmpty()) {
            ultimoAviso = "No se encontró librespot. Instalalo con: cargo install librespot --locked";
            return false;
        }
        barrerHuerfanosDeSesionesAnteriores();
        try {
            Files.createDirectories(RUTA_LOG.getParent());
            Files.createDirectories(RUTA_CACHE);

            proceso = new ProcessBuilder(argumentos(ejecutable.get()))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(RUTA_LOG.toFile()))
                    .start();
            guardarPid();
            registrarGanchoDeCierre();
        } catch (IOException fallo) {
            ultimoAviso = "No se pudo lanzar librespot: " + fallo.getMessage();
            return false;
        }
        return esperarRegistroEnSpotify();
    }

    /** @return {@code true} si el proceso sigue vivo */
    public boolean activo() {
        return proceso != null && proceso.isAlive();
    }

    /** @return el dispositivo tal como lo ve Spotify, si ya se registro */
    public Optional<DispositivoSpotify> dispositivo() {
        return Optional.ofNullable(dispositivo);
    }

    /** @return el motivo del ultimo fallo, si lo hubo */
    public Optional<String> ultimoAviso() {
        return Optional.ofNullable(ultimoAviso);
    }

    /** Detiene librespot, por las buenas o por las malas. */
    public synchronized void detener() {
        quitarGanchoDeCierre();
        matar(proceso);
        borrarPidGuardado();
        proceso = null;
        dispositivo = null;
    }

    @Override
    public void close() {
        detener();
    }

    // --- Construccion del comando ---

    /** Arma la linea de comandos. */
    private List<String> argumentos(Path ejecutable) {
        List<String> comando = new ArrayList<>();
        comando.add(ejecutable.toString());

        comando.add("--name");
        comando.add(configuracion.nombreDispositivo());

        // rodio es el backend de audio de Windows; viene en las features por defecto del crate.
        comando.add("--backend");
        comando.add("rodio");

        comando.add("--bitrate");
        comando.add(String.valueOf(configuracion.bitrate()));

        // Sin esto se oye muy bajo. Los valores por defecto de librespot son volumen al 50 % y
        // curva logaritmica, y un 50 % logaritmico ronda los -30 dB: casi un susurro. Se arranca
        comando.add("--initial-volume");
        comando.add(String.valueOf(configuracion.volumenInicial()));
        comando.add("--volume-ctrl");
        comando.add("linear");

        // La cache es lo que evita repetir el inicio de sesion en cada arranque.
        comando.add("--cache");
        comando.add(RUTA_CACHE.toString());

        comando.add("--enable-oauth");
        comando.add("--oauth-port");
        comando.add(String.valueOf(configuracion.puertoOauthLibrespot()));

        // El autoplay de Spotify encolaria canciones parecidas al terminar la pista, peleandose
        // con la cola y el arbol que gobiernan el orden. Aqui manda la estructura de datos.
        comando.add("--autoplay");
        comando.add("off");

        // Sin descubrimiento por la red: nos autenticamos por OAuth, y dejarlo activo publicaria
        // el dispositivo en la red del salon para que cualquiera lo tome.
        comando.add("--disable-discovery");

        return comando;
    }

    /** Busca el ejecutable de librespot. */
    static Optional<Path> localizarEjecutable() {
        // cargo instala aqui, y esta carpeta no siempre esta en el PATH de la sesion actual.
        Path enCargo = Path.of(System.getProperty("user.home"), ".cargo", "bin", nombreEjecutable());
        if (Files.isExecutable(enCargo)) {
            return Optional.of(enCargo);
        }
        String rutas = System.getenv("PATH");
        if (rutas != null) {
            for (String carpeta : rutas.split(java.io.File.pathSeparator)) {
                Path candidato = Path.of(carpeta, nombreEjecutable());
                if (Files.isExecutable(candidato)) {
                    return Optional.of(candidato);
                }
            }
        }
        return Optional.empty();
    }

    private static String nombreEjecutable() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? "librespot.exe"
                : "librespot";
    }

    // --- Health check ---

    /** Pregunta a Spotify hasta que el dispositivo aparezca. */
    private boolean esperarRegistroEnSpotify() {
        long limite = System.currentTimeMillis() + ESPERA_REGISTRO.toMillis();
        while (System.currentTimeMillis() < limite) {
            // Si el proceso murio, seguir sondeando seria perder 45 segundos para nada.
            if (!activo()) {
                ultimoAviso = "librespot se cerró al arrancar. Revisá " + RUTA_LOG + ": "
                        + ultimasLineasDelLog();
                return false;
            }
            Optional<DispositivoSpotify> encontrado =
                    api.buscarDispositivo(configuracion.nombreDispositivo());
            if (encontrado.isPresent()) {
                dispositivo = encontrado.get();
                ultimoAviso = null;
                return true;
            }
            if (!dormir(INTERVALO_SONDEO)) {
                return false;
            }
        }
        ultimoAviso = "Spotify no reconoció el dispositivo \""
                + configuracion.nombreDispositivo() + "\" en "
                + ESPERA_REGISTRO.toSeconds() + " s. Revisá " + RUTA_LOG + ": "
                + ultimasLineasDelLog();
        return false;
    }

    /** @return las ultimas lineas del registro, para que el aviso diga algo util */
    private static String ultimasLineasDelLog() {
        try {
            List<String> lineas = Files.readAllLines(RUTA_LOG);
            if (lineas.isEmpty()) {
                return "(el registro está vacío)";
            }
            return String.join(" | ", lineas.subList(Math.max(0, lineas.size() - 3), lineas.size()));
        } catch (IOException noSePudoLeer) {
            return "(no se pudo leer el registro)";
        }
    }

    private static boolean dormir(Duration cuanto) {
        try {
            Thread.sleep(cuanto.toMillis());
            return true;
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // --- Ciclo de vida del proceso ---

    /** Mata los librespot que hayan sobrevivido a una sesion anterior de la aplicacion. */
    private void barrerHuerfanosDeSesionesAnteriores() {
        leerPidGuardado(RUTA_PID)
                .flatMap(ProcessHandle::of)
                .filter(ProcesoLibrespot::pareceLibrespot)
                .ifPresent(ProcessHandle::destroyForcibly);
        borrarPidGuardado();
    }

    /** Comprueba que un identificador reutilizado no nos haga matar otra cosa. */
    static boolean pareceLibrespot(ProcessHandle candidato) {
        return candidato.info().command()
                .map(comando -> comando.toLowerCase(java.util.Locale.ROOT).contains("librespot"))
                .orElse(false);
    }

    /** Lee el identificador de proceso de la sesion anterior. */
    static Optional<Long> leerPidGuardado(Path archivo) {
        try {
            if (!Files.isRegularFile(archivo)) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(Files.readString(archivo).trim()));
        } catch (IOException | NumberFormatException archivoInservible) {
            return Optional.empty();
        }
    }

    /** Anota el identificador del proceso recien lanzado, para poder barrerlo si nos matan. */
    private void guardarPid() {
        try {
            Files.writeString(RUTA_PID, String.valueOf(proceso.pid()));
        } catch (IOException noSePudoAnotar) {
            // Perder la anotacion solo significa que un huerfano habria que matarlo a mano.
        }
    }

    private void borrarPidGuardado() {
        try {
            Files.deleteIfExists(RUTA_PID);
        } catch (IOException noSePudoBorrar) {
            // Un identificador viejo se descarta solo en el siguiente arranque.
        }
    }

    /** Registra un gancho que mata librespot si la maquina virtual termina. */
    private void registrarGanchoDeCierre() {
        Process aMatar = proceso;
        ganchoDeCierre = new Thread(() -> matar(aMatar), "cerrar-librespot");
        Runtime.getRuntime().addShutdownHook(ganchoDeCierre);
    }

    private void quitarGanchoDeCierre() {
        if (ganchoDeCierre == null) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(ganchoDeCierre);
        } catch (IllegalStateException yaSeEstaCerrando) {
            // La maquina virtual ya esta terminando y ejecutara el gancho igual.
        }
        ganchoDeCierre = null;
    }

    /** Cierra el proceso con un margen antes de forzarlo. */
    private static void matar(Process aMatar) {
        if (aMatar == null || !aMatar.isAlive()) {
            return;
        }
        aMatar.destroy();
        try {
            if (!aMatar.waitFor(ESPERA_CIERRE_ORDENADO.toSeconds(), TimeUnit.SECONDS)) {
                aMatar.destroyForcibly();
            }
        } catch (InterruptedException interrupcion) {
            aMatar.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}
