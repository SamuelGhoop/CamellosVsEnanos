package com.eia.reproductor.servicios.spotify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la deteccion de fin de pista.
 *
 * <p>Es la logica que en produccion solo se ejercita en vivo y que, si falla, hace saltar de
 * cancion sola en medio de la sustentacion. Aqui se prueba entera sin red.</p>
 */
class DetectorFinDePistaTest {

    private static final String LA_PISTA = "spotify:track:0GCaWksDZM7PV7mjdodhTT";
    private static final long DURACION = 189_239;

    private DetectorFinDePista detector;

    @BeforeEach
    void prepararDetector() {
        detector = new DetectorFinDePista();
        detector.vigilar(LA_PISTA);
    }

    private static EstadoReproductorSpotify sonandoEn(long posicionMs) {
        return new EstadoReproductorSpotify(LA_PISTA, posicionMs, DURACION, true, "Con el corazón");
    }

    private static EstadoReproductorSpotify detenidaEn(long posicionMs) {
        return new EstadoReproductorSpotify(LA_PISTA, posicionMs, DURACION, false, "Con el corazón");
    }

    /** Repite una lectura y devuelve si el detector confirmo el final en alguna. */
    private boolean observarVeces(EstadoReproductorSpotify estado, int veces) {
        boolean termino = false;
        for (int i = 0; i < veces; i++) {
            termino |= detector.observar(estado);
        }
        return termino;
    }

    @Nested
    @DisplayName("Reproducción normal")
    class ReproduccionNormal {

        @Test
        @DisplayName("Sonando a mitad de canción no es el final")
        void sonandoAMitadNoEsFinal() {
            assertFalse(observarVeces(sonandoEn(50_000), 10));
        }

        @Test
        @DisplayName("Sonando cerca del final tampoco: todavía suena")
        void sonandoCercaDelFinalNoEsFinal() {
            assertFalse(observarVeces(sonandoEn(DURACION - 500), 5));
        }

        @Test
        @DisplayName("Una pausa del usuario a mitad no dispara el salto")
        void pausaDelUsuarioNoEsFinal() {
            // Si esto fallara, pausar saltaría de canción: el peor error posible aquí.
            assertFalse(observarVeces(detenidaEn(50_000), 20));
        }

        @Test
        @DisplayName("Pausar tras escuchar media canción tampoco dispara")
        void pausaTrasEscucharUnRatoNoEsFinal() {
            detector.observar(sonandoEn(30_000));
            detector.observar(sonandoEn(60_000));
            detector.observar(sonandoEn(90_000));

            // Llegó lejos, pero no al final: la memoria de posición máxima lo distingue.
            assertFalse(observarVeces(detenidaEn(90_000), 10));
        }

        @Test
        @DisplayName("Una pista recién cargada y detenida en cero no es un final")
        void pistaReciencargadaNoEsFinal() {
            // Es el estado justo después de transferir y pausar: nunca sonó.
            assertFalse(observarVeces(detenidaEn(0), 10));
        }
    }

    @Nested
    @DisplayName("Fin de pista")
    class FinDePista {

        @Test
        @DisplayName("Detenida al borde del final, confirmado dos veces")
        void detenidaAlFinalTrasConfirmar() {
            assertFalse(detector.observar(detenidaEn(DURACION)), "una sola lectura no basta");
            assertTrue(detector.observar(detenidaEn(DURACION)));
        }

        @Test
        @DisplayName("Vale dentro del margen, no hace falta llegar exacto")
        void dentroDelMargen() {
            // El sondeo casi nunca cae justo en el último milisegundo.
            EstadoReproductorSpotify casi = detenidaEn(DURACION - DetectorFinDePista.MARGEN_FINAL_MS);

            assertTrue(observarVeces(casi, DetectorFinDePista.LECTURAS_PARA_CONFIRMAR));
        }

        @Test
        @DisplayName("Justo fuera del margen no cuenta")
        void fueraDelMargenNoCuenta() {
            EstadoReproductorSpotify lejos =
                    detenidaEn(DURACION - DetectorFinDePista.MARGEN_FINAL_MS - 1);

            assertFalse(observarVeces(lejos, 10));
        }

        @Test
        @DisplayName("El rebobinado a cero al terminar: el caso real")
        void rebobinadoACeroAlTerminar() {
            // Secuencia exacta medida contra Spotify. Al acabarse, la posición NO se queda
            // pegada a la duración: vuelve a ~0 con la pista todavía cargada.
            assertFalse(detector.observar(sonandoEn(187_737)));
            assertFalse(detector.observar(sonandoEn(188_901)));
            assertFalse(detector.observar(detenidaEn(209)), "una sola confirmación no basta");
            assertTrue(detector.observar(detenidaEn(209)));
        }

        @Test
        @DisplayName("Sin reproducción activa (204) es final si ya se había llegado al final")
        void sinReproduccionActivaTrasLlegarAlFinal() {
            detector.observar(sonandoEn(DURACION - 200));

            assertTrue(observarVeces(null, DetectorFinDePista.LECTURAS_PARA_CONFIRMAR));
        }

        @Test
        @DisplayName("Si Spotify se pasó a otra pista, la nuestra terminó")
        void spotifyCambioDePista() {
            EstadoReproductorSpotify otra = new EstadoReproductorSpotify(
                    "spotify:track:OTRA", 1_000, 200_000, true, "Otra");

            assertTrue(observarVeces(otra, DetectorFinDePista.LECTURAS_PARA_CONFIRMAR));
        }

        @Test
        @DisplayName("Avisa una sola vez por pista")
        void avisaUnaSolaVez() {
            observarVeces(detenidaEn(DURACION), DetectorFinDePista.LECTURAS_PARA_CONFIRMAR);

            // Un sondeo lento encadenaría dos saltos si esto no se respetara.
            assertFalse(observarVeces(detenidaEn(DURACION), 10));
        }
    }

    @Nested
    @DisplayName("Tolerancia a las lecturas inconsistentes de la API")
    class LecturasInconsistentes {

        @Test
        @DisplayName("Que la posición retroceda no significa nada")
        void posicionQueRetrocedeNoEsFinal() {
            // Caso real medido: 1604 → 3284 → 759 ms sin que la pista se reiniciara.
            assertFalse(detector.observar(sonandoEn(1_604)));
            assertFalse(detector.observar(sonandoEn(3_284)));
            assertFalse(detector.observar(sonandoEn(759)));
        }

        @Test
        @DisplayName("Una lectura suelta que parece el final no basta")
        void unaLecturaAisladaNoBasta() {
            assertFalse(detector.observar(detenidaEn(DURACION)));

            // La siguiente respuesta desmiente a la anterior: la cuenta se reinicia.
            assertFalse(detector.observar(sonandoEn(120_000)));
            assertFalse(detector.observar(detenidaEn(DURACION)),
                    "tras la contradicción hay que volver a confirmar desde cero");
            assertTrue(detector.observar(detenidaEn(DURACION)));
        }

        @Test
        @DisplayName("Un 204 aislado entre lecturas normales no dispara nada")
        void unNullAisladoNoDispara() {
            assertFalse(detector.observar(sonandoEn(10_000)));
            assertFalse(detector.observar(null));
            assertFalse(detector.observar(sonandoEn(12_000)));
            assertFalse(detector.observar(null));
        }
    }

    @Nested
    @DisplayName("Ciclo de vida")
    class CicloDeVida {

        @Test
        @DisplayName("Sin nada que vigilar no dispara nunca")
        void sinVigilarNoDispara() {
            DetectorFinDePista enBlanco = new DetectorFinDePista();

            assertFalse(enBlanco.observar(null));
            assertFalse(enBlanco.observar(new EstadoReproductorSpotify(
                    LA_PISTA, DURACION, DURACION, false, "x")));
        }

        @Test
        @DisplayName("Olvidar corta los avisos en seco")
        void olvidarCortaLosAvisos() {
            detector.observar(detenidaEn(DURACION));
            detector.olvidar();

            assertFalse(observarVeces(detenidaEn(DURACION), 10));
        }

        @Test
        @DisplayName("Vigilar una pista nueva permite avisar de nuevo")
        void vigilarOtraPistaReinicia() {
            observarVeces(detenidaEn(DURACION), DetectorFinDePista.LECTURAS_PARA_CONFIRMAR);

            detector.vigilar("spotify:track:LA_SIGUIENTE");
            EstadoReproductorSpotify siguiente = new EstadoReproductorSpotify(
                    "spotify:track:LA_SIGUIENTE", 100_000, 100_000, false, "Siguiente");

            assertTrue(observarVeces(siguiente, DetectorFinDePista.LECTURAS_PARA_CONFIRMAR));
        }

        @Test
        @DisplayName("Cambiar de pista descarta las confirmaciones a medias")
        void cambiarDePistaDescartaLoAcumulado() {
            detector.observar(detenidaEn(DURACION));

            detector.vigilar("spotify:track:OTRA_MAS");
            EstadoReproductorSpotify nueva = new EstadoReproductorSpotify(
                    "spotify:track:OTRA_MAS", 0, 200_000, true, "Nueva");

            assertFalse(detector.observar(nueva));
        }
    }
}
