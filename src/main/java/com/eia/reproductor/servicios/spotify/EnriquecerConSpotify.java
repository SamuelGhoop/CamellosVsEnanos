package com.eia.reproductor.servicios.spotify;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.servicios.PersistenciaService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Rellena la URI de Spotify de las canciones que ya estan en la biblioteca. */
public final class EnriquecerConSpotify {
    /** Pausa entre busquedas, para no castigar la API con una rafaga. */
    private static final long PAUSA_ENTRE_BUSQUEDAS_MS = 250;

    private EnriquecerConSpotify() {
    }

    /** Punto de entrada. */
    public static void main(String[] argumentos) {
        boolean aplicar = argumentos.length > 0 && "--aplicar".equals(argumentos[0]);

        System.out.println("== Buscar en Spotify las canciones de la biblioteca ==");
        System.out.println(aplicar
                ? "MODO APLICAR: se van a guardar los cambios."
                : "MODO PRUEBA: no se guarda nada. Usá --aplicar cuando estés conforme.");
        System.out.println();

        Optional<ClienteWebApiSpotify> api = conectar();
        if (api.isEmpty()) {
            return;
        }

        PersistenciaService persistencia = new PersistenciaService();
        List<Cancion> biblioteca = persistencia.cargar();
        if (biblioteca.isEmpty()) {
            System.out.println("La biblioteca está vacía.");
            return;
        }
        System.out.println("canciones en la biblioteca: " + biblioteca.size());
        System.out.println();

        List<Cancion> encontradas = buscarTodas(api.get(), biblioteca);

        System.out.println();
        System.out.println("== Resumen ==");
        System.out.println("con URI nueva : " + encontradas.size());
        System.out.println("sin encontrar : "
                + biblioteca.stream().filter(c -> !c.tieneUriSpotify()).count());

        if (encontradas.isEmpty()) {
            return;
        }
        if (!aplicar) {
            System.out.println();
            System.out.println("No se guardó nada. Volvé a correrlo con --aplicar para guardar.");
            return;
        }
        guardar(persistencia, biblioteca);
    }

    /** Recorre la biblioteca buscando lo que falta. */
    private static List<Cancion> buscarTodas(ClienteWebApiSpotify api, List<Cancion> biblioteca) {
        List<Cancion> encontradas = new ArrayList<>();
        for (Cancion cancion : biblioteca) {
            if (cancion.tieneUriSpotify()) {
                System.out.printf("  [ya] %-32s ya tenía URI%n", recortar(cancion.getTitulo()));
                continue;
            }
            Optional<PistaSpotify> pista =
                    api.buscarPista(cancion.getTitulo(), cancion.getArtista());
            if (pista.isEmpty()) {
                System.out.printf("  [--] %-32s %s%n", recortar(cancion.getTitulo()),
                        api.ultimoAviso().orElse("sin coincidencia clara"));
            } else {
                cancion.setUriSpotify(pista.get().uri());
                encontradas.add(cancion);
                System.out.printf("  [OK] %-32s %s  (%s)%n",
                        recortar(cancion.getTitulo()),
                        pista.get().uri(),
                        recortar(pista.get().artista()));
            }
            pausar();
        }
        return encontradas;
    }

    /** Guarda la biblioteca, dejando antes una copia de seguridad. */
    private static void guardar(PersistenciaService persistencia, List<Cancion> biblioteca) {
        Path original = Path.of("data", "biblioteca.json");
        Path copia = Path.of("data", "biblioteca.antes-de-spotify.json");
        try {
            if (Files.isRegularFile(original)) {
                Files.copy(original, copia, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("copia de seguridad: " + copia);
            }
        } catch (IOException fallo) {
            System.out.println("NO se pudo hacer la copia de seguridad: " + fallo.getMessage());
            System.out.println("Se cancela por precaución. No se guardó nada.");
            return;
        }
        System.out.println(persistencia.guardar(biblioteca)
                ? "biblioteca guardada."
                : "NO se pudo guardar: " + persistencia.ultimoAviso().orElse("motivo desconocido"));
    }

    /** @return el cliente listo, o vacio si no hay sesion de Spotify */
    private static Optional<ClienteWebApiSpotify> conectar() {
        Optional<ConfiguracionSpotify> configuracion = ConfiguracionSpotify.cargar();
        if (configuracion.isEmpty()) {
            System.out.println("Falta config/spotify.properties.");
            return Optional.empty();
        }
        AutenticacionSpotify autenticacion =
                new AutenticacionSpotify(configuracion.get(), new AlmacenTokenSpotify());
        if (autenticacion.tokenSinInteraccion().isEmpty()) {
            System.out.println("No hay sesión de Spotify. Corré antes el DiagnosticoSpotify.");
            return Optional.empty();
        }
        return Optional.of(new ClienteWebApiSpotify(autenticacion));
    }

    private static void pausar() {
        try {
            Thread.sleep(PAUSA_ENTRE_BUSQUEDAS_MS);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return "(sin nombre)";
        }
        return texto.length() <= 32 ? texto : texto.substring(0, 29) + "...";
    }
}
