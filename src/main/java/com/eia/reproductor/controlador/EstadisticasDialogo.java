package com.eia.reproductor.controlador;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.servicios.EstadisticasBiblioteca;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Ventana con el resumen de lo mas escuchado.
 *
 * <p>Solo coloca en pantalla lo que ya calculo {@link EstadisticasBiblioteca}: aqui no se suma ni
 * se ordena nada. Se arma desde codigo y no desde un FXML porque el contenido depende de cuantas
 * canciones hayan sonado —el podio puede tener de cero a cinco filas— y un FXML con cinco filas
 * fijas que a veces se ocultan es mas dificil de seguir que este metodo.</p>
 */
public final class EstadisticasDialogo {

    /** La insignia verde, que hasta ahora estaba en el proyecto sin que nadie la mostrara. */
    private static final String RUTA_INSIGNIA = "/imagenes/spidey/insignia-verde.png";

    private static final double LADO_INSIGNIA = 40;

    private EstadisticasDialogo() {
    }

    /**
     * Arma el contenido de la ventana.
     *
     * @param resumen las cuentas ya hechas
     * @return el nodo listo para pasarselo a {@link VentanaPixel#montar}
     */
    public static VBox construir(EstadisticasBiblioteca resumen) {
        VBox cuerpo = new VBox(14);
        cuerpo.getStyleClass().add("cuerpo-dialogo");
        cuerpo.setAlignment(Pos.TOP_CENTER);
        insignia().ifPresent(cuerpo.getChildren()::add);

        if (resumen.vacias()) {
            Label aviso = new Label("Todavía no has reproducido nada.\n"
                    + "Dale al play y vuelve a abrir esta ventana.");
            aviso.getStyleClass().add("texto-cuerpo");
            aviso.setWrapText(true);
            cuerpo.getChildren().add(aviso);
            return cuerpo;
        }

        cuerpo.getChildren().addAll(
                fichas(
                        ficha(String.valueOf(resumen.totalReproducciones()), "REPRODUCCIONES"),
                        ficha(String.valueOf(resumen.minutosEscuchados()), "MINUTOS ESCUCHADOS"),
                        ficha(String.valueOf(resumen.distintasSonadas()), "CANCIONES DISTINTAS")),
                fichas(
                        ficha(oSinDato(resumen.artistaTop()), "ARTISTA MÁS ESCUCHADO"),
                        ficha(oSinDato(resumen.generoTop()), "GÉNERO MÁS ESCUCHADO")),
                podio(resumen.masEscuchadas()));
        return cuerpo;
    }

    /**
     * La insignia que corona la ventana.
     *
     * <p>Devuelve vacio si el archivo no esta en el classpath, en vez de reventar: una ventana de
     * estadisticas sin adorno sigue sirviendo, y una excepcion al abrirla no.</p>
     */
    private static Optional<ImageView> insignia() {
        try (InputStream archivo = EstadisticasDialogo.class.getResourceAsStream(RUTA_INSIGNIA)) {
            if (archivo == null) {
                return Optional.empty();
            }
            ImageView vista = new ImageView(new Image(archivo));
            // Sin suavizado y a un tamanio fijo, como el resto de los sprites: interpolar un pixel
            // art lo deja borroso.
            vista.setSmooth(false);
            vista.setPreserveRatio(true);
            vista.setFitHeight(LADO_INSIGNIA);
            return Optional.of(vista);
        } catch (Exception fallo) {
            return Optional.empty();
        }
    }

    /** Una fila de fichas, centrada y con la misma separacion que las del panel de bienvenida. */
    private static HBox fichas(VBox... contenido) {
        HBox fila = new HBox(contenido);
        fila.getStyleClass().add("fila-estructuras");
        return fila;
    }

    /** Una ficha: el numero grande arriba y que mide debajo. */
    private static VBox ficha(String valor, String etiqueta) {
        Label numero = new Label(valor);
        numero.getStyleClass().add("ficha-titulo");

        Label pie = new Label(etiqueta);
        pie.getStyleClass().add("ficha-estructura-nombre");

        VBox caja = new VBox(numero, pie);
        caja.getStyleClass().add("ficha-estructura");
        return caja;
    }

    /** El podio de las mas repetidas, en orden. */
    private static VBox podio(List<Cancion> masEscuchadas) {
        Label encabezado = new Label("• LAS MÁS ESCUCHADAS");
        encabezado.getStyleClass().add("panel-encabezado");

        VBox bloque = new VBox(6, encabezado);
        bloque.setAlignment(Pos.TOP_LEFT);
        for (int i = 0; i < masEscuchadas.size(); i++) {
            bloque.getChildren().add(filaDelPodio(i + 1, masEscuchadas.get(i)));
        }
        return bloque;
    }

    /** Puesto y titulo a la izquierda, veces reproducida a la derecha. */
    private static HBox filaDelPodio(int puesto, Cancion cancion) {
        Label posicion = new Label(puesto + ".");
        posicion.getStyleClass().add("ficha-titulo");

        Label titulo = new Label(cancion.getTitulo() + "  —  " + cancion.getArtista());
        titulo.getStyleClass().add("ficha-detalle");

        Region empuje = new Region();
        HBox.setHgrow(empuje, Priority.ALWAYS);

        int veces = cancion.getVecesReproducida();
        Label cuenta = new Label(veces + (veces == 1 ? " vez" : " veces"));
        cuenta.getStyleClass().add("texto-tenue");

        HBox fila = new HBox(8, posicion, titulo, empuje, cuenta);
        fila.setAlignment(Pos.CENTER_LEFT);
        return fila;
    }

    /** Evita que la ficha salga con la palabra "null" cuando no hay dato. */
    private static String oSinDato(String texto) {
        return texto == null || texto.isBlank() ? Cancion.TEXTO_DESCONOCIDO : texto;
    }
}
