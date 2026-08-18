package com.eia.reproductor.tmp;

import com.eia.reproductor.App;
import com.eia.reproductor.IntroDeArranque;
import com.eia.reproductor.IntroDeArranque.Movimiento;
import com.eia.reproductor.servicios.EntornoJavaFx;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import java.io.File;

class VerMovimientos {
    @Test
    void capturarCadaMovimiento() throws Exception {
        if (!EntornoJavaFx.disponible()) return;
        for (Movimiento m : Movimiento.values()) {
            CountDownLatch hecho = new CountDownLatch(1);
            Platform.runLater(() -> {
                App.cargarFuentePixel();
                IntroDeArranque intro = new IntroDeArranque();
                intro.mostrar();
                // irA arma el guion y se coloca; el arranque automatico ya no lo pisa.
                intro.irA(m, () -> {
                    System.out.printf("MOV %-9s instante=%.3f  %s%n",
                            m.name(), m.instante(), m.etiqueta());
                    foto("m-" + m.name() + ".png");
                    intro.saltar();
                    hecho.countDown();
                });
            });
            hecho.await(10, TimeUnit.SECONDS);
        }
    }

    private static void foto(String nombre) {
        for (javafx.stage.Window v : javafx.stage.Window.getWindows()) {
            if (v.getScene() == null || !v.isShowing()) continue;
            try {
                WritableImage img = v.getScene().snapshot(null);
                int w = (int) img.getWidth(), h = (int) img.getHeight();
                var d = new java.awt.image.BufferedImage(w, h, 2);
                var r = img.getPixelReader();
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
                    d.setRGB(x, y, r.getArgb(x, y));
                ImageIO.write(d, "png", new File(System.getProperty("java.io.tmpdir"), nombre));
            } catch (Exception fallo) {
                System.out.println("FALLO " + fallo);
            }
            return;
        }
        System.out.println("SIN VENTANA");
    }
}
