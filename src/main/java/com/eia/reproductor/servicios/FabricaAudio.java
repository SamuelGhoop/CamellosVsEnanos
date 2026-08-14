package com.eia.reproductor.servicios;

import com.eia.reproductor.servicios.spotify.AudioSpotifyService;

/**
 * Arma el reproductor de audio con todas las fuentes que la maquina pueda ofrecer.
 *
 * <p><b>Por que existe.</b> Antes el controlador construia el enrutador con {@code new} y una lista
 * fija de fuentes. Eso significaba que sumar una fuente obligaba a editar el controlador, que es
 * justo lo que el diseno tenia que evitar. Ahora el controlador pide un reproductor y no sabe de
 * cuantas piezas esta hecho; la decision de que fuentes existen vive aqui, en la capa de servicios,
 * que es donde corresponde.</p>
 *
 * <p>Agregar una fuente nueva es editar {@link #crear()} y nada mas.</p>
 */
public final class FabricaAudio {

    private FabricaAudio() {
    }

    /**
     * Construye el reproductor de audio de la aplicacion.
     *
     * <p>El orden importa: es el orden de preferencia con el que el enrutador consulta a las
     * fuentes. La simulada va la ultima porque acepta cualquier cancion, y esa es la garantia de
     * que el reproductor nunca se queda mudo.</p>
     *
     * @return un reproductor listo para usar
     */
    public static AudioRuteado crear() {
        AudioRuteado enrutador = new AudioRuteado(
                new AudioLocalService(),
                new AudioSimuladoService());

        // Spotify va por delante cuando esta configurado. Si no lo esta, devuelve vacio y aqui no
        // pasa nada: la aplicacion arranca igual con audio local. Esta es la unica linea que hubo
        // que escribir para sumar la fuente de Spotify; el controlador, la interfaz grafica y las
        // estructuras de datos quedaron sin tocar.
        AudioSpotifyService.crearSiEstaConfigurado()
                .ifPresent(enrutador::agregarFuentePrioritaria);

        return enrutador;
    }
}
