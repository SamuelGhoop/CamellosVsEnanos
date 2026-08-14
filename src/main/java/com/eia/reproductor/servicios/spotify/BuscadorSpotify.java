package com.eia.reproductor.servicios.spotify;

import java.util.Optional;

/**
 * Punto de entrada sencillo para buscar la URI de una cancion en Spotify.
 *
 * <p>Existe para que el dialogo de agregar canciones pueda autocompletar el campo de Spotify sin
 * tener que montar la autenticacion, el cliente y el manejo de errores cada vez. Si Spotify no esta
 * configurado, devuelve vacio y ya: no configurar Spotify no es un fallo, es una opcion.</p>
 *
 * <p>Bloquea mientras consulta, asi que se llama desde un hilo aparte, nunca desde el de la
 * interfaz.</p>
 */
public final class BuscadorSpotify {

    /** Se construye una sola vez: crearlo en cada busqueda releeria el token del disco. */
    private static ClienteWebApiSpotify api;
    private static boolean intentadoConectar;

    private BuscadorSpotify() {
    }

    /**
     * Busca la URI de una cancion por titulo e interprete.
     *
     * @param titulo  titulo de la cancion
     * @param artista interprete, puede ir vacio
     * @return la URI {@code spotify:track:...}, o vacio si no hay sesion o no hay coincidencia
     */
    public static Optional<String> buscarUri(String titulo, String artista) {
        return conectar()
                .flatMap(cliente -> cliente.buscarPista(titulo, artista))
                .map(PistaSpotify::uri);
    }

    private static synchronized Optional<ClienteWebApiSpotify> conectar() {
        if (intentadoConectar) {
            return Optional.ofNullable(api);
        }
        intentadoConectar = true;

        Optional<ConfiguracionSpotify> configuracion = ConfiguracionSpotify.cargar();
        if (configuracion.isEmpty()) {
            return Optional.empty();
        }
        AutenticacionSpotify autenticacion =
                new AutenticacionSpotify(configuracion.get(), new AlmacenTokenSpotify());
        if (!autenticacion.haySesion()) {
            return Optional.empty();
        }
        api = new ClienteWebApiSpotify(autenticacion);
        return Optional.of(api);
    }
}
