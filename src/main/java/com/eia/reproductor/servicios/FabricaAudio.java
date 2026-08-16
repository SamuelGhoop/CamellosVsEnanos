package com.eia.reproductor.servicios;

import com.eia.reproductor.servicios.spotify.AudioSpotifyService;

/** Arma el reproductor de audio con todas las fuentes que la maquina pueda ofrecer. */
public final class FabricaAudio {
    private FabricaAudio() {
    }

    /** Construye el reproductor de audio de la aplicacion. */
    public static AudioRuteado crear() {
        AudioRuteado enrutador = new AudioRuteado(
                new AudioLocalService(),
                new AudioSimuladoService());

        // Spotify va por delante cuando esta configurado. Si no lo esta, devuelve vacio y aqui no
        // pasa nada: la aplicacion arranca igual con audio local. Esta es la unica linea que hubo
        AudioSpotifyService.crearSiEstaConfigurado()
                .ifPresent(enrutador::agregarFuentePrioritaria);

        return enrutador;
    }
}
