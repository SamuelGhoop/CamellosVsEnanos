package com.eia.reproductor;

import com.eia.reproductor.IntroDeArranque.Compas;
import javafx.util.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del reparto de tiempos y del ajuste a la pantalla.
 *
 * <p>Las dos cosas son calculo puro, sin ventanas: se pueden comprobar sin levantar JavaFX y con
 * medidas de pantallas que no son la de esta maquina.</p>
 */
class CompasIntroTest {

    /**
     * Suma del ritmo base, tomada de la propia clase.
     *
     * <p>Estaba escrita a mano y se quedó desfasada en cuanto se sumó el desgarro al guion: tres
     * pruebas fallaron por el número, no por el comportamiento. Derivarla evita que vuelva a
     * pasar cada vez que se toque el ritmo.</p>
     */
    private static final double BASE = IntroDeArranque.BASE_TOTAL;

    @Nested
    @DisplayName("Reparto de la duracion")
    class Reparto {

        @Test
        @DisplayName("Sin audio se queda en el ritmo base")
        void sinAudio() {
            assertEquals(BASE / 1000, new Compas(null).total().toSeconds(), 0.001);
            assertEquals(BASE / 1000, new Compas(Duration.UNKNOWN).total().toSeconds(), 0.001);
            assertEquals(BASE / 1000, new Compas(Duration.ZERO).total().toSeconds(), 0.001);
        }

        @Test
        @DisplayName("Con una pista dentro de los topes, dura exactamente lo que la pista")
        void duraLoQueLaPista() {
            assertEquals(7, new Compas(Duration.seconds(7)).total().toSeconds(), 0.001);
        }

        @Test
        @DisplayName("Una pista cortísima se estira al mínimo")
        void pistaCorta() {
            // Sin el minimo, cuatro actos en 1,5 s serian un parpadeo.
            assertEquals(4, new Compas(Duration.seconds(1.5)).total().toSeconds(), 0.001);
        }

        @Test
        @DisplayName("Una canción entera se recorta al máximo")
        void pistaLarga() {
            assertEquals(10, new Compas(Duration.seconds(185)).total().toSeconds(), 0.001);
        }

        @Test
        @DisplayName("Los actos conservan su proporción sea cual sea el total")
        void proporcionesIntactas() {
            // Se mide contra el tiempo REPARTIDO, no contra el total. Antes se comparaba con el
            // total y la prueba era incorrecta desde que existe el encendido: como el tubo dura lo
            // mismo siempre, ocupa un 15 % de una presentación de 4 s y un 6 % de una de 10, así
            // que el peso de cada acto sobre el total tiene que cambiar. Lo que no puede cambiar
            // es su peso dentro de lo que se reparte.
            double pesoEsperado = IntroDeArranque.BASE_ACTO_TRES / BASE;
            for (double segundos : new double[] {4, 5.67, 7, 10}) {
                Compas compas = new Compas(Duration.seconds(segundos));
                double repartido = compas.total().toMillis() - IntroDeArranque.MS_ENCENDIDO;
                double peso = compas.de(IntroDeArranque.BASE_ACTO_TRES).toMillis() / repartido;
                assertEquals(pesoEsperado, peso, 1e-9, "con " + segundos + " s");
            }
        }

        @Test
        @DisplayName("Los cuatro actos y el desgarro suman el total, ni más ni menos")
        void losTramosSumanElTotal() {
            Compas compas = new Compas(Duration.seconds(9));
            // Los cinco tramos del guion, en orden: acto 1, desgarro, actos 2, 3 y 4. Se toman de
            // la clase y no a mano: escritos a mano ya fallaron dos veces al retocar el ritmo.
            double suma = compas.de(IntroDeArranque.BASE_ACTO_UNO).toMillis()
                    + compas.de(IntroDeArranque.BASE_GLITCH).toMillis()
                    + compas.de(IntroDeArranque.BASE_ACTO_DOS).toMillis()
                    + compas.de(IntroDeArranque.BASE_ACTO_TRES).toMillis()
                    + compas.de(IntroDeArranque.BASE_ACTO_CUATRO).toMillis();

            // Los cinco tramos mas el encendido dan el total exacto: es lo que hace que el
            // fundido del acto 4 caiga justo al acabar la pista. Si se agrega un tramo nuevo al
            // guion sin sumarlo al ritmo base, esta prueba lo caza.
            assertEquals(compas.total().toMillis(), suma + IntroDeArranque.MS_ENCENDIDO, 0.001);
        }

        @Test
        @DisplayName("El encendido se descuenta del reparto, no alarga el total")
        void elEncendidoSeDescuenta() {
            // Los 600 ms del tubo son fijos. Lo que se estira o encoge es lo que queda para los
            // actos, de modo que la presentación siga acabando cuando acaba la pista.
            Compas compas = new Compas(Duration.seconds(8));
            assertEquals(8000, compas.total().toMillis(), 0.001, "el total no puede crecer");

            double actos = compas.de(IntroDeArranque.BASE_ACTO_UNO).toMillis()
                    + compas.de(IntroDeArranque.BASE_GLITCH).toMillis()
                    + compas.de(IntroDeArranque.BASE_ACTO_DOS).toMillis()
                    + compas.de(IntroDeArranque.BASE_ACTO_TRES).toMillis()
                    + compas.de(IntroDeArranque.BASE_ACTO_CUATRO).toMillis();

            assertEquals(8000 - 600, actos, 0.001, "a los actos les toca el total menos el tubo");
        }

        @Test
        @DisplayName("Con la pista más corta posible, a los actos les queda tiempo de sobra")
        void elEncendidoNoSeComeLosActos() {
            // Con el minimo de 4 s, tras descontar el tubo quedan 3,4 para los cinco tramos.
            Compas compas = new Compas(Duration.seconds(1));
            assertTrue(compas.de(IntroDeArranque.BASE_ACTO_UNO).toMillis() > 0,
                    "el acto 1 se quedó sin tiempo");
        }

        @Test
        @DisplayName("El desgarro es breve: menos de un cuarto de la presentación")
        void elDesgarroEsBreve() {
            // Por encima de un cuarto deja de leerse como un efecto y parece que se colgó.
            for (double segundos : new double[] {4, 6.37, 10}) {
                Compas compas = new Compas(Duration.seconds(segundos));
                double peso = compas.de(IntroDeArranque.BASE_GLITCH).toMillis()
                        / compas.total().toMillis();
                assertTrue(peso < 0.25, "el desgarro pesa " + peso + " con " + segundos + " s");
            }
        }
    }

    @Nested
    @DisplayName("Ajuste a la pantalla")
    class Ajuste {

        @Test
        @DisplayName("En una pantalla holgada se usa el área deseada")
        void pantallaHolgada() {
            double[] area = IntroDeArranque.areaQueCabe(1536, 912);
            assertEquals(900, area[0], 0.001);
            assertEquals(640, area[1], 0.001);
        }

        @Test
        @DisplayName("En 1024x768 se encoge el área, nunca el marco")
        void pantallaJusta() {
            double[] area = IntroDeArranque.areaQueCabe(1024, 768);

            // 1024 - 40 de margen - 158 de marco = 826; 768 - 40 - 121 = 607.
            assertEquals(826, area[0], 0.001);
            assertEquals(607, area[1], 0.001);

            // Y lo que importa: la ventana entera cabe en la pantalla.
            assertTrue(area[0] + 158 <= 1024, "se sale de ancho");
            assertTrue(area[1] + 121 <= 768, "se sale de alto");
        }

        @Test
        @DisplayName("El área nunca crece más allá de lo deseado")
        void nuncaMasGrande() {
            double[] area = IntroDeArranque.areaQueCabe(3840, 2160);
            assertEquals(900, area[0], 0.001);
            assertEquals(640, area[1], 0.001);
        }

        @Test
        @DisplayName("En una pantalla diminuta se planta en el mínimo legible")
        void pantallaDiminuta() {
            double[] area = IntroDeArranque.areaQueCabe(640, 480);
            assertEquals(520, area[0], 0.001);
            assertEquals(380, area[1], 0.001);
        }
    }
}
