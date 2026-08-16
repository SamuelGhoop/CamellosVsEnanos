package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del enrutador de fuentes de audio. */
class AudioRuteadoTest {
    /**
     * Fuente de prueba: acepta las canciones cuyo titulo esta en una lista y anota que le pidieron.
     */
    private static final class FuenteFalsa implements ReproductorAudio {
        private final String nombre;
        private final List<String> titulosQueAcepta;
        private final ReadOnlyLongWrapper posicion = new ReadOnlyLongWrapper(0);
        private final ReadOnlyLongWrapper duracion = new ReadOnlyLongWrapper(0);
        private final BooleanProperty sonando = new SimpleBooleanProperty(false);
        private final List<String> llamadas = new ArrayList<>();
        private boolean operativa = true;
        private boolean porRed;
        private Runnable alTerminar;
        private java.util.function.Consumer<String> alFallar;

        FuenteFalsa(String nombre, String... titulosQueAcepta) {
            this.nombre = nombre;
            this.titulosQueAcepta = List.of(titulosQueAcepta);
        }

        @Override public boolean requiereRed() {
            return porRed;
        }

        @Override public void reproducir(Cancion cancion) {
            llamadas.add("reproducir:" + cancion.getTitulo());
            sonando.set(true);
        }
        @Override public void pausar() {
            llamadas.add("pausar");
            sonando.set(false);
        }
        @Override public void reanudar() {
            llamadas.add("reanudar");
            sonando.set(true);
        }
        @Override public void detener() {
            llamadas.add("detener");
            sonando.set(false);
        }
        @Override public void buscarPosicion(long milisegundos) {
            llamadas.add("buscar:" + milisegundos);
            posicion.set(milisegundos);
        }
        @Override public void avanzarRelativo(long milisegundos) {
            buscarPosicion(posicion.get() + milisegundos);
        }
        @Override public ReadOnlyLongProperty posicionMsProperty() {
            return posicion.getReadOnlyProperty();
        }
        @Override public ReadOnlyLongProperty duracionMsProperty() {
            return duracion.getReadOnlyProperty();
        }
        @Override public BooleanProperty reproduciendoProperty() {
            return sonando;
        }
        @Override public boolean disponible() {
            return operativa;
        }
        @Override public boolean puedeReproducir(Cancion cancion) {
            return cancion != null && titulosQueAcepta.contains(cancion.getTitulo());
        }
        @Override public String nombreFuente() {
            return nombre;
        }
        @Override public void setAlTerminarPista(Runnable callback) {
            this.alTerminar = callback;
        }
        @Override public void setAlFallar(java.util.function.Consumer<String> callback) {
            this.alFallar = callback;
        }

        void terminarPista() {
            if (alTerminar != null) {
                alTerminar.run();
            }
        }

        void fallar(String mensaje) {
            if (alFallar != null) {
                alFallar.accept(mensaje);
            }
        }
    }

    private static Cancion cancion(String titulo) {
        return new Cancion(titulo);
    }

    @Test
    @DisplayName("Elige la primera fuente que sepa reproducir la cancion")
    void eligeLaPrimeraQueSabe() {
        FuenteFalsa primera = new FuenteFalsa("Primera", "A");
        FuenteFalsa segunda = new FuenteFalsa("Segunda", "B");
        AudioRuteado enrutador = new AudioRuteado(primera, segunda);

        enrutador.reproducir(cancion("B"));

        assertTrue(primera.llamadas.isEmpty(), "La primera no sabe reproducir B");
        assertEquals(List.of("reproducir:B"), segunda.llamadas);
        assertEquals("Segunda", enrutador.nombreFuente());
    }

    @Test
    @DisplayName("El orden de registro es el orden de preferencia")
    void respetaElOrdenDePreferencia() {
        FuenteFalsa preferida = new FuenteFalsa("Preferida", "A");
        FuenteFalsa respaldo = new FuenteFalsa("Respaldo", "A");
        AudioRuteado enrutador = new AudioRuteado(preferida, respaldo);

        enrutador.reproducir(cancion("A"));

        assertEquals(List.of("reproducir:A"), preferida.llamadas);
        assertTrue(respaldo.llamadas.isEmpty());
    }

    @Test
    @DisplayName("Salta las fuentes que no estan disponibles")
    void saltaLasNoDisponibles() {
        FuenteFalsa caida = new FuenteFalsa("Caida", "A");
        caida.operativa = false;
        FuenteFalsa viva = new FuenteFalsa("Viva", "A");
        AudioRuteado enrutador = new AudioRuteado(caida, viva);

        enrutador.reproducir(cancion("A"));

        assertTrue(caida.llamadas.isEmpty());
        assertEquals(List.of("reproducir:A"), viva.llamadas);
    }

    @Test
    @DisplayName("Sin ninguna fuente capaz no hace nada y lo admite")
    void sinFuenteCapazNoHaceNada() {
        FuenteFalsa unica = new FuenteFalsa("Unica", "A");
        AudioRuteado enrutador = new AudioRuteado(unica);

        assertFalse(enrutador.puedeReproducir(cancion("Z")));
        enrutador.reproducir(cancion("Z"));

        assertTrue(unica.llamadas.isEmpty());
        assertEquals("Sin fuente", enrutador.nombreFuente());
    }

    @Test
    @DisplayName("Al cambiar de fuente detiene la anterior")
    void detieneLaFuenteAnteriorAlCambiar() {
        FuenteFalsa deA = new FuenteFalsa("DeA", "A");
        FuenteFalsa deB = new FuenteFalsa("DeB", "B");
        AudioRuteado enrutador = new AudioRuteado(deA, deB);

        enrutador.reproducir(cancion("A"));
        enrutador.reproducir(cancion("B"));

        assertEquals(List.of("reproducir:A", "detener"), deA.llamadas);
        assertEquals(List.of("reproducir:B"), deB.llamadas);
    }

    @Test
    @DisplayName("Las propiedades siguen a la fuente activa, incluso tras cambiarla")
    void reexponeLasPropiedadesDeLaFuenteActiva() {
        FuenteFalsa deA = new FuenteFalsa("DeA", "A");
        FuenteFalsa deB = new FuenteFalsa("DeB", "B");
        AudioRuteado enrutador = new AudioRuteado(deA, deB);

        enrutador.reproducir(cancion("A"));
        deA.posicion.set(4_000);
        deA.duracion.set(200_000);
        assertEquals(4_000, enrutador.posicionMsProperty().get());
        assertEquals(200_000, enrutador.duracionMsProperty().get());
        assertTrue(enrutador.reproduciendoProperty().get());

        enrutador.reproducir(cancion("B"));
        deB.posicion.set(1_500);
        deB.duracion.set(90_000);
        assertEquals(1_500, enrutador.posicionMsProperty().get(), "Debe mirar la nueva fuente");
        assertEquals(90_000, enrutador.duracionMsProperty().get());

        // La fuente vieja ya no manda sobre el enrutador.
        deA.posicion.set(999_999);
        assertEquals(1_500, enrutador.posicionMsProperty().get());
    }

    @Test
    @DisplayName("Solo el fin de pista de la fuente activa hace avanzar la cola")
    void soloCuentaElFinDeLaFuenteActiva() {
        FuenteFalsa deA = new FuenteFalsa("DeA", "A");
        FuenteFalsa deB = new FuenteFalsa("DeB", "B");
        AudioRuteado enrutador = new AudioRuteado(deA, deB);
        List<String> avisos = new ArrayList<>();
        enrutador.setAlTerminarPista(() -> avisos.add("fin"));

        enrutador.reproducir(cancion("A"));

        // Una fuente vieja que se apaga tarde no debe saltar de cancion.
        deB.terminarPista();
        assertTrue(avisos.isEmpty());

        deA.terminarPista();
        assertEquals(List.of("fin"), avisos);
    }

    @Test
    @DisplayName("Reenvia hacia arriba el fallo de una fuente que no esta sonando")
    void reenviaElFalloDeUnaFuenteInactiva() {
        FuenteFalsa deA = new FuenteFalsa("DeA", "A");
        AudioRuteado enrutador = new AudioRuteado(deA);
        List<String> avisos = new ArrayList<>();
        enrutador.setAlFallar(avisos::add);

        deA.fallar("archivo corrupto");

        assertEquals(List.of("archivo corrupto"), avisos);
    }

    // --- Recuperacion ante fallos ---

    @Test
    @DisplayName("Si la fuente activa falla, baja a la siguiente y sigue sonando")
    void caeALaSiguienteFuenteAlFallar() {
        FuenteFalsa primera = new FuenteFalsa("Primera", "A");
        FuenteFalsa respaldo = new FuenteFalsa("Respaldo", "A");
        AudioRuteado enrutador = new AudioRuteado(primera, respaldo);

        enrutador.reproducir(cancion("A"));
        primera.fallar("se cayó la red");

        assertEquals("Respaldo", enrutador.nombreFuente());
        assertTrue(respaldo.llamadas.contains("reproducir:A"), "Nunca se queda mudo");
        assertTrue(primera.llamadas.contains("detener"));
    }

    @Test
    @DisplayName("Al caer de fuente retoma donde iba, no desde cero")
    void retomaLaPosicionAlCambiarDeFuente() {
        FuenteFalsa primera = new FuenteFalsa("Primera", "A");
        FuenteFalsa respaldo = new FuenteFalsa("Respaldo", "A");
        AudioRuteado enrutador = new AudioRuteado(primera, respaldo);

        enrutador.reproducir(cancion("A"));
        primera.duracion.set(200_000);
        primera.posicion.set(45_000);

        // El respaldo ya sabe cuanto dura, asi que el salto es inmediato.
        respaldo.duracion.set(200_000);
        primera.fallar("se cayó la red");

        assertTrue(respaldo.llamadas.contains("buscar:45000"),
                "Debe retomar en el segundo 45, no en el 0. Llamadas: " + respaldo.llamadas);
    }

    @Test
    @DisplayName("Espera a conocer la duracion antes de retomar la posicion")
    void esperaLaDuracionParaRetomar() {
        FuenteFalsa primera = new FuenteFalsa("Primera", "A");
        FuenteFalsa respaldo = new FuenteFalsa("Respaldo", "A");
        AudioRuteado enrutador = new AudioRuteado(primera, respaldo);

        enrutador.reproducir(cancion("A"));
        primera.duracion.set(200_000);
        primera.posicion.set(30_000);
        primera.fallar("archivo corrupto");

        // Un MP3 no publica su duracion hasta cargar la cabecera: saltar antes recortaria a cero.
        assertFalse(respaldo.llamadas.contains("buscar:30000"), "Todavía no sabe cuánto dura");

        respaldo.duracion.set(180_000);
        assertTrue(respaldo.llamadas.contains("buscar:30000"), "Al saber la duración, salta");
    }

    @Test
    @DisplayName("Cada fuente se intenta una sola vez: no hay bucle infinito")
    void noCiclaCuandoFallanTodas() {
        FuenteFalsa primera = new FuenteFalsa("Primera", "A");
        FuenteFalsa segunda = new FuenteFalsa("Segunda", "A");
        FuenteFalsa ultima = new FuenteFalsa("Ultima", "A");
        AudioRuteado enrutador = new AudioRuteado(primera, segunda, ultima);
        List<String> avisos = new ArrayList<>();
        enrutador.setAlFallar(avisos::add);

        enrutador.reproducir(cancion("A"));
        primera.fallar("falla 1");
        segunda.fallar("falla 2");
        ultima.fallar("falla 3");

        // La cadena baja una sola vez por fuente y se detiene: si volviera a mirar desde el
        // principio, esto no terminaria nunca.
        assertEquals(1, contarApariciones(primera.llamadas, "reproducir:A"));
        assertEquals(1, contarApariciones(segunda.llamadas, "reproducir:A"));
        assertEquals(1, contarApariciones(ultima.llamadas, "reproducir:A"));

        // El ultimo fallo ya no tiene a donde caer y se reporta tal cual.
        assertTrue(avisos.get(avisos.size() - 1).contains("falla 3"));
    }

    private static int contarApariciones(List<String> lista, String valor) {
        return (int) lista.stream().filter(valor::equals).count();
    }

    // --- Politica de red ---

    @Test
    @DisplayName("Evitar la red descarta las fuentes que dependen de internet")
    void evitarRedDescartaLasFuentesDeRed() {
        FuenteFalsa porRed = new FuenteFalsa("PorRed", "A");
        porRed.porRed = true;
        FuenteFalsa local = new FuenteFalsa("Local", "A");
        AudioRuteado enrutador = new AudioRuteado(porRed, local);

        enrutador.setEvitarRed(true);
        enrutador.reproducir(cancion("A"));

        assertTrue(enrutador.evitandoRed());
        assertTrue(porRed.llamadas.isEmpty());
        assertEquals("Local", enrutador.nombreFuente());
    }

    @Test
    @DisplayName("Activar el interruptor a mitad de cancion se baja de la fuente de red en el acto")
    void evitarRedSeAplicaEnCaliente() {
        FuenteFalsa porRed = new FuenteFalsa("PorRed", "A");
        porRed.porRed = true;
        FuenteFalsa local = new FuenteFalsa("Local", "A");
        AudioRuteado enrutador = new AudioRuteado(porRed, local);

        enrutador.reproducir(cancion("A"));
        assertEquals("PorRed", enrutador.nombreFuente());

        // Es el caso del salón: la red falla y hay que cambiar sin reiniciar la aplicación.
        enrutador.setEvitarRed(true);

        assertEquals("Local", enrutador.nombreFuente());
        assertTrue(local.llamadas.contains("reproducir:A"));
    }

    @Test
    @DisplayName("Volver a AUTO devuelve la preferencia a la fuente de red")
    void volverAAutoRecuperaLaFuenteDeRed() {
        FuenteFalsa porRed = new FuenteFalsa("PorRed", "A");
        porRed.porRed = true;
        FuenteFalsa local = new FuenteFalsa("Local", "A");
        AudioRuteado enrutador = new AudioRuteado(porRed, local);

        enrutador.setEvitarRed(true);
        enrutador.reproducir(cancion("A"));
        assertEquals("Local", enrutador.nombreFuente());

        enrutador.setEvitarRed(false);
        enrutador.reproducir(cancion("A"));

        assertFalse(enrutador.evitandoRed());
        assertEquals("PorRed", enrutador.nombreFuente());
    }

    @Test
    @DisplayName("Si no queda fuente sin red, detiene y avisa en vez de callarse")
    void avisaCuandoNoHayFuenteSinRed() {
        FuenteFalsa porRed = new FuenteFalsa("PorRed", "A");
        porRed.porRed = true;
        AudioRuteado enrutador = new AudioRuteado(porRed);
        List<String> avisos = new ArrayList<>();
        enrutador.setAlFallar(avisos::add);

        enrutador.reproducir(cancion("A"));
        enrutador.setEvitarRed(true);

        // Lo inaceptable seria seguir sonando por la red justo despues de que pidieron evitarla.
        assertTrue(porRed.llamadas.contains("detener"));
        assertEquals(1, avisos.size());
        assertTrue(avisos.get(0).contains("sin conexión"), "Aviso recibido: " + avisos);
    }

    @Test
    @DisplayName("Sin fuentes locales, evitar la red deja al enrutador no disponible")
    void evitarRedPuedeDejarSinFuentes() {
        FuenteFalsa porRed = new FuenteFalsa("PorRed", "A");
        porRed.porRed = true;
        AudioRuteado enrutador = new AudioRuteado(porRed);

        enrutador.setEvitarRed(true);

        assertFalse(enrutador.disponible());
        assertFalse(enrutador.puedeReproducir(cancion("A")));
    }

    @Test
    @DisplayName("Los controles de transporte llegan a la fuente activa")
    void delegaLosControlesDeTransporte() {
        FuenteFalsa unica = new FuenteFalsa("Unica", "A");
        AudioRuteado enrutador = new AudioRuteado(unica);

        enrutador.reproducir(cancion("A"));
        enrutador.pausar();
        enrutador.reanudar();
        enrutador.buscarPosicion(7_000);
        enrutador.avanzarRelativo(3_000);
        enrutador.detener();

        assertEquals(
                List.of("reproducir:A", "pausar", "reanudar", "buscar:7000", "buscar:10000",
                        "detener"),
                unica.llamadas);
    }

    @Test
    @DisplayName("Sin fuente activa los controles no revientan")
    void losControlesSonInocuosSinFuenteActiva() {
        AudioRuteado enrutador = new AudioRuteado(new FuenteFalsa("Unica", "A"));

        enrutador.pausar();
        enrutador.reanudar();
        enrutador.detener();
        enrutador.buscarPosicion(1_000);
        enrutador.avanzarRelativo(-500);

        assertEquals(0, enrutador.posicionMsProperty().get());
    }

    @Test
    @DisplayName("Una fuente prioritaria se cuela al principio de la lista")
    void laFuentePrioritariaVaPrimero() {
        FuenteFalsa antigua = new FuenteFalsa("Antigua", "A");
        AudioRuteado enrutador = new AudioRuteado(antigua);
        FuenteFalsa nueva = new FuenteFalsa("Nueva", "A");

        // Es el gesto exacto de la fase 7b: sumar Spotify sin tocar nada mas.
        enrutador.agregarFuentePrioritaria(nueva);
        enrutador.reproducir(cancion("A"));

        assertEquals(List.of("reproducir:A"), nueva.llamadas);
        assertTrue(antigua.llamadas.isEmpty());
    }

    @Test
    @DisplayName("El enrutador esta disponible si al menos una fuente lo esta")
    void disponibleSiAlgunaLoEsta() {
        FuenteFalsa caida = new FuenteFalsa("Caida", "A");
        caida.operativa = false;
        FuenteFalsa viva = new FuenteFalsa("Viva", "B");

        assertFalse(new AudioRuteado(caida).disponible());
        assertTrue(new AudioRuteado(caida, viva).disponible());
    }

    @Test
    @DisplayName("Registrar una fuente nula se ignora sin romper nada")
    void ignoraLasFuentesNulas() {
        FuenteFalsa buena = new FuenteFalsa("Buena", "A");
        AudioRuteado enrutador = new AudioRuteado(null, buena);

        enrutador.reproducir(cancion("A"));

        assertEquals(List.of("reproducir:A"), buena.llamadas);
    }

    @Test
    @DisplayName("Repetir la misma fuente no la detiene entre canciones")
    void noSeDetieneSiLaFuenteEsLaMisma() {
        FuenteFalsa unica = new FuenteFalsa("Unica", "A", "B");
        AudioRuteado enrutador = new AudioRuteado(unica);

        enrutador.reproducir(cancion("A"));
        enrutador.reproducir(cancion("B"));

        assertEquals(List.of("reproducir:A", "reproducir:B"), unica.llamadas);
    }
}
