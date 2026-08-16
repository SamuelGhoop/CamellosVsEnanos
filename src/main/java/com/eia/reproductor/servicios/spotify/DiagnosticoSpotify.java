package com.eia.reproductor.servicios.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Herramienta de linea de comandos para comprobar que la sesion de Spotify funciona. */
public final class DiagnosticoSpotify {
    private static final String URL_PERFIL = "https://api.spotify.com/v1/me";

    /** Pista con la que se comprueba que sale audio. */
    private static final String URI_DE_PRUEBA = "spotify:track:0GCaWksDZM7PV7mjdodhTT";

    private DiagnosticoSpotify() {
    }

    /** Punto de entrada del diagnostico. */
    public static void main(String[] argumentos) {
        System.out.println("== Diagnóstico de Spotify ==");

        Optional<ConfiguracionSpotify> configuracion = ConfiguracionSpotify.cargar();
        if (configuracion.isEmpty()) {
            System.out.println("FALTA config/spotify.properties (o está sin rellenar).");
            System.out.println("Copiá config/spotify.properties.example y poné el client.id.");
            return;
        }
        ConfiguracionSpotify config = configuracion.get();
        System.out.println("config      : client.id=" + enmascarar(config.clientId()));
        System.out.println("              redirect=" + config.redirectUri());
        System.out.println("              device=" + config.nombreDispositivo());

        AlmacenTokenSpotify almacen = new AlmacenTokenSpotify();
        AutenticacionSpotify autenticacion = new AutenticacionSpotify(config, almacen);
        System.out.println("sesión previa: " + (autenticacion.haySesion() ? "sí" : "no"));

        if (!autenticacion.haySesion()) {
            System.out.println();
            System.out.println(">> Se va a abrir el navegador. Autorizá y volvé acá.");
        }

        Optional<String> token = autenticacion.tokenDeAcceso();
        if (token.isEmpty()) {
            System.out.println("FALLÓ: " + autenticacion.ultimoAviso().orElse("motivo desconocido"));
            return;
        }
        System.out.println("token       : " + enmascarar(token.get()));
        almacen.cargar().ifPresent(guardado -> {
            System.out.println("vence       : " + Instant.ofEpochMilli(guardado.venceEnMillis()));
            System.out.println("renovable   : " + guardado.puedeRenovarse());
        });

        System.out.println();
        System.out.println("== GET /v1/me ==");
        consultarPerfil(token.get());

        System.out.println();
        System.out.println("== Renovación anticipada ==");
        comprobarRenovacion(config, almacen);

        if (argumentos.length > 0 && "--librespot".equals(argumentos[0])) {
            System.out.println();
            comprobarLibrespot(config, autenticacion);
        } else {
            System.out.println();
            System.out.println("(pasá --librespot para probar también el paso 2)");
        }
    }

    /** Lanza librespot y comprueba que Spotify lo reconoce. */
    private static void comprobarLibrespot(ConfiguracionSpotify config,
                                           AutenticacionSpotify autenticacion) {
        System.out.println("== Paso 2: librespot ==");

        Optional<java.nio.file.Path> ejecutable = ProcesoLibrespot.localizarEjecutable();
        System.out.println("ejecutable  : " + ejecutable.map(Object::toString).orElse("NO ENCONTRADO"));
        if (ejecutable.isEmpty()) {
            System.out.println("Instalalo con: cargo install librespot --locked");
            return;
        }

        ClienteWebApiSpotify api = new ClienteWebApiSpotify(autenticacion);
        System.out.println("dispositivos antes: " + nombresDe(api));

        try (ProcesoLibrespot librespot = new ProcesoLibrespot(config, api)) {
            System.out.println("lanzando... (la primera vez abre el navegador para que librespot "
                    + "inicie su propia sesión)");
            long inicio = System.currentTimeMillis();
            boolean listo = librespot.iniciar();
            long tardo = System.currentTimeMillis() - inicio;

            System.out.println("registrado  : " + listo + "  (en " + tardo + " ms)");
            if (!listo) {
                System.out.println("motivo      : " + librespot.ultimoAviso().orElse("desconocido"));
                return;
            }
            DispositivoSpotify dispositivo = librespot.dispositivo().orElseThrow();
            System.out.println("device id   : " + dispositivo.id());
            System.out.println("nombre      : " + dispositivo.nombre());
            System.out.println("tipo        : " + dispositivo.tipo());
            System.out.println("activo      : " + dispositivo.activo() + "   <- antes de transferir");
            System.out.println("dispositivos ahora: " + nombresDe(api));

            System.out.println();
            System.out.println("== Paso 3: PUT /v1/me/player ==");
            transferir(api, dispositivo);

            System.out.println("proceso vivo: " + librespot.activo());
        }

        System.out.println("tras cerrar : sin procesos huérfanos "
                + "(verificalo con: Get-Process librespot)");
    }

    /** Transfiere la reproduccion al dispositivo y comprueba que Spotify lo dio por activo. */
    private static void transferir(ClienteWebApiSpotify api, DispositivoSpotify dispositivo) {
        boolean aceptada = api.transferirA(dispositivo.id());
        System.out.println("transferido : " + aceptada);
        if (!aceptada) {
            System.out.println("motivo      : " + api.ultimoAviso().orElse("desconocido"));
            return;
        }
        for (int intento = 1; intento <= 10; intento++) {
            Optional<DispositivoSpotify> ahora = api.buscarDispositivo(dispositivo.nombre());
            if (ahora.isPresent() && ahora.get().activo()) {
                System.out.println("activo      : true   <- la reproducción ya sale por librespot");
                boolean silenciado = api.silenciarRepeticionYAleatorio(dispositivo.id());
                System.out.println("repeat/shuffle off: " + silenciado
                        + (silenciado ? "" : "   motivo: " + api.ultimoAviso().orElse("?")));

                // play:false no significa "en silencio", solo "no cambies el estado". Si la
                // cuenta venia reproduciendo, hay que pausar explicitamente.
                System.out.println("pausado     : " + api.pausar(dispositivo.id()));
                // La API tarda un momento en reflejar la pausa; leer al instante da el valor viejo.
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException interrupcion) {
                    Thread.currentThread().interrupt();
                }
                estadoDelReproductor(api);
                sonarDeVerdad(api, dispositivo);
                return;
            }
            try {
                Thread.sleep(700);
            } catch (InterruptedException interrupcion) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.println("activo      : false  <- Spotify aceptó pero no lo marcó activo");
    }

    /** Muestra el estado del reproductor tras transferir. */
    private static void estadoDelReproductor(ClienteWebApiSpotify api) {
        Optional<JsonObject> estado = api.obtener("/me/player");
        if (estado.isEmpty()) {
            System.out.println("player      : sin reproducción activa (204) — "
                    + api.ultimoAviso().orElse("correcto"));
            return;
        }
        JsonObject player = estado.get();
        System.out.println("is_playing  : " + campo(player, "is_playing")
                + "   <- false es lo correcto: transferir no debe arrancar la música");
        System.out.println("progress_ms : " + campo(player, "progress_ms"));
        if (player.has("item") && player.get("item").isJsonObject()) {
            JsonObject pista = player.getAsJsonObject("item");
            System.out.println("pista       : " + campo(pista, "name")
                    + "  (" + campo(pista, "duration_ms") + " ms)");
            System.out.println("uri         : " + campo(pista, "uri"));
        }
        if (player.has("device") && player.get("device").isJsonObject()) {
            JsonObject dispositivo = player.getAsJsonObject("device");
            System.out.println("device      : " + campo(dispositivo, "name"));
            // Por defecto librespot arranca al 50 % y con curva logaritmica: se oye a susurro.
            System.out.println("volumen     : " + campo(dispositivo, "volume_percent")
                    + " %   <- debe ser 100");
        }
        System.out.println("repeat/shuffle: " + campo(player, "repeat_state")
                + " / " + campo(player, "shuffle_state")
                + "   <- deben quedar en off para no pelear con la cola");
    }

    /** Paso 4: reproduce una pista de verdad, salta de posicion y pausa. */
    private static void sonarDeVerdad(ClienteWebApiSpotify api, DispositivoSpotify dispositivo) {
        System.out.println();
        System.out.println("== Paso 4: play / seek / pause ==");
        System.out.println("pista de prueba: " + URI_DE_PRUEBA);

        if (!api.reproducir(dispositivo.id(), URI_DE_PRUEBA, 0)) {
            System.out.println("play        : false   motivo: " + api.ultimoAviso().orElse("?"));
            return;
        }
        System.out.println("play        : true    >>> DEBERÍAS ESTAR OYENDO MÚSICA <<<");

        for (int segundo = 1; segundo <= 3; segundo++) {
            esperar(1500);
            System.out.println("  progress  : " + progresoDe(api) + " ms");
        }

        System.out.println("seek a 60 s : " + api.buscarPosicion(dispositivo.id(), 60_000));
        esperar(1500);
        System.out.println("  progress  : " + progresoDe(api) + " ms   <- debe rondar los 60000");

        System.out.println("pause       : " + api.pausar(dispositivo.id()));
        esperar(1500);
        System.out.println("  is_playing: " + api.obtener("/me/player")
                .map(estado -> campo(estado, "is_playing")).orElse("(sin estado)"));

        sondearHastaElFinal(api, dispositivo);
    }

    /** Paso 5: sondea hasta que el detector confirma el fin de la pista. */
    private static void sondearHastaElFinal(ClienteWebApiSpotify api,
                                            DispositivoSpotify dispositivo) {
        System.out.println();
        System.out.println("== Paso 5: sondeo y fin de pista ==");

        DetectorFinDePista detector = new DetectorFinDePista();
        detector.vigilar(URI_DE_PRUEBA);

        Optional<EstadoReproductorSpotify> inicial = api.estadoDelReproductor();
        long duracion = inicial.map(EstadoReproductorSpotify::duracionMs).orElse(0L);
        if (duracion <= 0) {
            System.out.println("no se pudo leer la duración; se omite");
            return;
        }
        long arranque = Math.max(0, duracion - 8_000);
        System.out.println("saltando a " + arranque + " ms de " + duracion + " y reanudando...");
        api.buscarPosicion(dispositivo.id(), arranque);
        api.reanudar(dispositivo.id());

        long limite = System.currentTimeMillis() + 40_000;
        while (System.currentTimeMillis() < limite) {
            esperar(1000);
            EstadoReproductorSpotify estado = api.estadoDelReproductor().orElse(null);
            System.out.println("  sondeo    : " + descripcionDe(estado));
            if (detector.observar(estado)) {
                System.out.println(">>> FIN DE PISTA CONFIRMADO — aquí se llamaría a siguiente() "
                        + "del modo activo <<<");
                return;
            }
        }
        System.out.println("no se confirmó el fin en 40 s");
    }

    private static String descripcionDe(EstadoReproductorSpotify estado) {
        if (estado == null) {
            return "(sin reproducción activa)";
        }
        return "pos=" + estado.posicionMs() + "/" + estado.duracionMs()
                + "  sonando=" + estado.reproduciendo();
    }

    private static String progresoDe(ClienteWebApiSpotify api) {
        return api.obtener("/me/player").map(estado -> campo(estado, "progress_ms"))
                .orElse("(sin estado)");
    }

    private static void esperar(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }

    private static String nombresDe(ClienteWebApiSpotify api) {
        var dispositivos = api.dispositivos();
        if (dispositivos.isEmpty()) {
            return "(ninguno)";
        }
        return dispositivos.stream().map(DispositivoSpotify::nombre).toList().toString();
    }

    private static void consultarPerfil(String token) {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(URL_PERFIL))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        try {
            HttpResponse<String> respuesta = HttpClient.newHttpClient()
                    .send(peticion, HttpResponse.BodyHandlers.ofString());
            System.out.println("HTTP        : " + respuesta.statusCode());
            if (respuesta.statusCode() != 200) {
                System.out.println("cuerpo      : " + respuesta.body());
                return;
            }
            JsonObject perfil = JsonParser.parseString(respuesta.body()).getAsJsonObject();
            System.out.println("usuario     : " + campo(perfil, "display_name"));
            System.out.println("id          : " + campo(perfil, "id"));
            // country y product solo llegan con user-read-private, que a proposito no se pide.
            System.out.println("país        : " + campo(perfil, "country")
                    + "   <- ausente es correcto: no pedimos user-read-private");
            System.out.println("producto    : " + campo(perfil, "product"));
        } catch (Exception fallo) {
            System.out.println("FALLÓ la llamada: " + fallo.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /** Comprueba que la renovacion funciona sin volver a abrir el navegador. */
    private static void comprobarRenovacion(ConfiguracionSpotify config,
                                            AlmacenTokenSpotify almacen) {
        Optional<TokenSpotify> guardado = almacen.cargar();
        if (guardado.isEmpty() || !guardado.get().puedeRenovarse()) {
            System.out.println("No hay refresh token guardado: no se puede comprobar.");
            return;
        }
        AutenticacionSpotify enFrio = new AutenticacionSpotify(config, almacen);
        boolean silencioso = enFrio.tokenSinInteraccion().isPresent();
        System.out.println("arranque en frío sin navegador: " + (silencioso ? "OK" : "FALLÓ"));
    }

    private static String campo(JsonObject objeto, String nombre) {
        if (!objeto.has(nombre) || objeto.get(nombre).isJsonNull()) {
            return "(ausente)";
        }
        // getAsString revienta con booleanos y numeros; el JSON del player trae de los tres tipos.
        return objeto.get(nombre).getAsJsonPrimitive().toString().replace("\"", "");
    }

    /** Muestra lo justo para reconocer un valor sin dejarlo legible en la consola. */
    private static String enmascarar(String valor) {
        if (valor == null || valor.length() < 8) {
            return "(vacío)";
        }
        return valor.substring(0, 4) + "…" + valor.substring(valor.length() - 4)
                + " (" + valor.length() + " car.)";
    }
}
