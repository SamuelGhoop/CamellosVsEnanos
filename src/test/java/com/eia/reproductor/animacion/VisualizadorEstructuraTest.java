package com.eia.reproductor.animacion;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.EstructuraVisual;
import com.eia.reproductor.modos.ModoAlfabetico;
import com.eia.reproductor.modos.ModoAleatorio;
import com.eia.reproductor.modos.ModoOrdenLlegada;
import com.eia.reproductor.modos.ModoReproduccion;
import com.eia.reproductor.servicios.EntornoJavaFx;
import javafx.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pruebas del dibujante de estructuras.
 *
 * <p>No comprueban que el dibujo sea bonito —eso hay que mirarlo— sino que ninguna forma lo tumbe.
 * Importa porque el visualizador se abre en la sustentacion: un arbol vacio, uno degenerado o un
 * titulo larguisimo no pueden reventar delante del profesor.</p>
 */
class VisualizadorEstructuraTest {

    @BeforeAll
    static void prepararEntorno() {
        assumeTrue(EntornoJavaFx.disponible(), "No hay entorno gráfico para levantar JavaFX.");
    }

    private static List<Cancion> canciones(String... titulos) {
        List<Cancion> lista = new ArrayList<>();
        for (String titulo : titulos) {
            lista.add(new Cancion(titulo));
        }
        return lista;
    }

    /** Dibuja dentro del hilo de JavaFX, que es el unico que puede tocar un {@code Canvas}. */
    private static void dibujar(EstructuraVisual estructura) {
        EntornoJavaFx.enElHiloFx(() -> {
            VisualizadorEstructura visualizador = new VisualizadorEstructura();
            new Scene(visualizador.nodo());
            assertDoesNotThrow(() -> visualizador.mostrar(estructura));
        });
    }

    @Test
    @DisplayName("Dibuja las tres estructuras con canciones cargadas")
    void dibujaLasTres() {
        for (ModoReproduccion modo : List.of(
                new ModoAleatorio(), new ModoOrdenLlegada(), new ModoAlfabetico())) {
            modo.cargar(canciones("Zombie", "Africa", "Creep", "Angel"));
            modo.siguiente();
            dibujar(modo.estructuraVisual());
        }
    }

    @Test
    @DisplayName("Dibuja las tres estructuras vacías")
    void dibujaLasTresVacias() {
        for (ModoReproduccion modo : List.of(
                new ModoAleatorio(), new ModoOrdenLlegada(), new ModoAlfabetico())) {
            modo.cargar(List.of());
            dibujar(modo.estructuraVisual());
        }
    }

    @Test
    @DisplayName("Dibuja un árbol degenerado, que es el caso que más se estira")
    void dibujaArbolDegenerado() {
        // Entrando ya ordenadas, el árbol se convierte en una rama de veinte niveles: es la forma
        // que rompía el dibujo anterior, porque la separación se hacía menor que las cajas.
        ModoAlfabetico modo = new ModoAlfabetico();
        List<Cancion> ordenadas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ordenadas.add(new Cancion("Cancion " + (char) ('a' + i)));
        }
        modo.cargar(ordenadas);
        modo.siguiente();

        dibujar(modo.estructuraVisual());
    }

    @Test
    @DisplayName("Un título larguísimo se recorta en vez de desbordarse")
    void tituloLarguisimo() {
        ModoAlfabetico modo = new ModoAlfabetico();
        modo.cargar(canciones("A".repeat(300), "B"));

        dibujar(modo.estructuraVisual());
    }

    @Test
    @DisplayName("Un anillo con más canciones de las que caben no rompe nada")
    void anilloMuyPoblado() {
        ModoAleatorio modo = new ModoAleatorio();
        List<Cancion> muchas = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            muchas.add(new Cancion("Pista " + i));
        }
        modo.cargar(muchas);
        modo.siguiente();

        dibujar(modo.estructuraVisual());
    }

    @Test
    @DisplayName("Sin estructura no dibuja y no revienta")
    void sinEstructura() {
        dibujar(null);
    }
}
