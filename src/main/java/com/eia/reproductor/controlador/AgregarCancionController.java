package com.eia.reproductor.controlador;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.ResultadoBusquedaApi;
import com.eia.reproductor.servicios.EtiquetasAudioService;
import com.eia.reproductor.servicios.MetadataApiService;
import com.eia.reproductor.servicios.spotify.BuscadorSpotify;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controlador del formulario de agregar y editar canciones.
 *
 * <p>Reune las tres vias que pide el enunciado en una sola pantalla:</p>
 * <ol>
 *   <li><b>Desde archivo:</b> se elige un MP3 o WAV, se leen sus etiquetas ID3 y con ellas se
 *       consulta la API automaticamente.</li>
 *   <li><b>Buscando en linea:</b> el usuario escribe, elige un resultado y el formulario se
 *       autocompleta. Vincular un archivo es opcional.</li>
 *   <li><b>A mano:</b> el formulario esta siempre disponible, funcione o no internet.</li>
 * </ol>
 *
 * <p>Las tres desembocan en el mismo formulario, que es lo unico que se guarda. El dialogo no toca
 * la biblioteca: devuelve unos {@link DatosCancion} y el {@link PrincipalController} decide si
 * crear o editar a traves del servicio.</p>
 *
 * <p><b>Ninguna llamada de red ocurre en el hilo de la interfaz.</b> Las busquedas van dentro de un
 * {@link Task} y el resultado se aplica cuando termina, para que la ventana nunca se congele.</p>
 */
public class AgregarCancionController implements Initializable {

    private static final String RUTA_VISTA = "/vista/agregar-cancion.fxml";
    private static final String RUTA_ESTILOS = "/vista/estilos.css";
    private static final String RUTA_PORTADA_PLACEHOLDER = "/imagenes/portada-placeholder.png";
    private static final String RUTA_FAVORITO = "/imagenes/spidey/favorito.png";

    private static final int SEGUNDOS_POR_MINUTO = 60;
    private static final int ANIO_MAXIMO_RAZONABLE = 2200;
    private static final double LADO_MINIATURA = 40;


    @FXML private Button botonElegirArchivo;
    @FXML private Button botonQuitarArchivo;
    @FXML private Label etiquetaArchivo;

    @FXML private TextField campoConsulta;
    @FXML private Button botonBuscarApi;
    @FXML private Label etiquetaEstadoBusqueda;
    @FXML private ListView<ResultadoBusquedaApi> listaResultados;

    @FXML private ImageView vistaPortada;
    @FXML private Label etiquetaFuente;
    @FXML private TextField campoTitulo;
    @FXML private TextField campoArtista;
    @FXML private TextField campoAlbum;
    @FXML private TextField campoGenero;
    @FXML private TextField campoUriSpotify;
    @FXML private TextField campoAnio;
    @FXML private TextField campoDuracion;

    @FXML private Slider deslizadorCalificacion;
    @FXML private Label etiquetaCalificacion;
    @FXML private Button botonFavorito;
    @FXML private ImageView iconoFavorito;
    @FXML private Label etiquetaFavorito;
    @FXML private Label etiquetaError;

    /** Estado de la estrella de favorita. Sustituye a la casilla de verificacion. */
    private boolean favorita;
    @FXML private Button botonGuardar;

    private final MetadataApiService api = new MetadataApiService();
    private final EtiquetasAudioService etiquetasAudio = new EtiquetasAudioService();
    private final ObservableList<ResultadoBusquedaApi> resultados = FXCollections.observableArrayList();

    private Stage escenario;
    private DatosCancion resultado;
    private Image portadaPorDefecto;
    private String urlPortadaElegida;
    private Path archivoElegido;

    // ------------------------------------------------------------------
    // Apertura del dialogo
    // ------------------------------------------------------------------

    /**
     * Abre el formulario y espera a que el usuario termine.
     *
     * @param duenio  ventana propietaria, para que el dialogo sea modal sobre ella
     * @param aEditar cancion cuyos datos precargar, o {@code null} para crear una nueva
     * @return los datos capturados, o vacio si el usuario cancelo
     */
    public static Optional<DatosCancion> mostrar(Window duenio, Cancion aEditar) {
        // Idempotente: garantiza la tipografia de pixeles aunque el dialogo se abra desde un punto
        // de entrada que no sea la aplicacion completa.
        com.eia.reproductor.App.cargarFuentePixel();
        try {
            FXMLLoader cargador = new FXMLLoader(
                    AgregarCancionController.class.getResource(RUTA_VISTA));
            Parent raiz = cargador.load();
            AgregarCancionController controlador = cargador.getController();

            Stage escenario = new Stage();
            escenario.initModality(Modality.WINDOW_MODAL);
            if (duenio != null) {
                escenario.initOwner(duenio);
            }
            String titulo = aEditar == null ? "Agregar canción" : "Editar canción";
            escenario.setTitle(titulo);
            escenario.setResizable(false);

            // Barra de titulo propia en vez de la blanca de Windows: junto al marco pixel, la del
            // sistema delataba que la estetica era solo una capa por encima.
            VentanaPixel.montar(escenario, titulo, raiz);

            controlador.prepararse(escenario, aEditar);
            escenario.showAndWait();
            return Optional.ofNullable(controlador.resultado);
        } catch (IOException excepcion) {
            System.err.println("[ERROR] No se pudo abrir el formulario de canción: "
                    + excepcion.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void initialize(URL ubicacion, ResourceBundle recursos) {
        deslizadorCalificacion.valueProperty().addListener((observable, anterior, actual) ->
                etiquetaCalificacion.setText(Math.round(actual.floatValue()) + " / 100"));
        etiquetaCalificacion.setText("0 / 100");

        cargarPortadaPorDefecto();
        configurarListaDeResultados();
        botonQuitarArchivo.setDisable(true);

        cargarRecurso(RUTA_FAVORITO).ifPresent(iconoFavorito::setImage);
        establecerFavorita(false);
    }

    /** Alterna la estrella de favorita. Sustituye a la antigua casilla de verificacion. */
    @FXML
    private void alternarFavorita() {
        establecerFavorita(!favorita);
    }

    /**
     * Refleja el estado de favorita en la estrella.
     *
     * <p>Apagada se muestra atenuada y en escala de grises; encendida, a todo color. Se usa un
     * efecto sobre la misma imagen en vez de dos PNG distintos: es un solo asset que mantener.</p>
     */
    private void establecerFavorita(boolean valor) {
        favorita = valor;
        iconoFavorito.setOpacity(valor ? 1.0 : 0.45);
        iconoFavorito.setEffect(valor ? null : new javafx.scene.effect.ColorAdjust(0, -1, 0, 0));
        etiquetaFavorito.setText(valor ? "FAVORITA" : "NO ES FAVORITA");
    }

    private static Optional<Image> cargarRecurso(String ruta) {
        try (InputStream flujo = AgregarCancionController.class.getResourceAsStream(ruta)) {
            return flujo == null ? Optional.empty() : Optional.of(new Image(flujo));
        } catch (IOException excepcion) {
            return Optional.empty();
        }
    }

    private void prepararse(Stage escenario, Cancion aEditar) {
        this.escenario = escenario;
        if (aEditar == null) {
            // Nada que precargar: el titulo de la ventana ya lo puso quien abrio el dialogo.
            return;
        }

        DatosCancion datos = DatosCancion.de(aEditar);
        campoTitulo.setText(datos.titulo());
        campoArtista.setText(datos.artista());
        campoAlbum.setText(datos.album());
        campoGenero.setText(datos.genero());
        campoUriSpotify.setText(nuloAVacio(datos.uriSpotify()));
        campoAnio.setText(datos.anio() > 0 ? String.valueOf(datos.anio()) : "");
        campoDuracion.setText(datos.duracionSegundos() > 0
                ? formatearDuracion(datos.duracionSegundos()) : "");
        deslizadorCalificacion.setValue(datos.calificacion());
        establecerFavorita(datos.favorita());

        urlPortadaElegida = datos.urlPortadaRemota();
        if (datos.rutaArchivo() != null && !datos.rutaArchivo().isBlank()) {
            archivoElegido = Path.of(datos.rutaArchivo());
            mostrarArchivoElegido();
        }
        mostrarPortadaLocalSiExiste(aEditar);
        campoConsulta.setText((datos.artista() + " " + datos.titulo()).trim());
    }

    // ------------------------------------------------------------------
    // Via A: archivo local
    // ------------------------------------------------------------------

    @FXML
    private void elegirArchivo() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Elegir archivo de audio");
        selector.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Audio (MP3, WAV)", "*.mp3", "*.wav"));

        File elegido = selector.showOpenDialog(escenario);
        if (elegido == null) {
            return;
        }
        archivoElegido = elegido.toPath();
        mostrarArchivoElegido();
        limpiarError();

        Optional<EtiquetasAudioService.Etiquetas> etiquetas = etiquetasAudio.leer(archivoElegido);
        if (etiquetas.isEmpty()) {
            mostrarEstado("No se pudieron leer las etiquetas del archivo. Completá los datos a mano.");
            return;
        }

        volcarEtiquetas(etiquetas.get());
        // Con lo leido del archivo se arma la consulta y se busca sola, que es la gracia de esta via.
        String consulta = etiquetas.get().consultaSugerida(archivoElegido);
        campoConsulta.setText(consulta);
        buscarEnLinea();
    }

    @FXML
    private void quitarArchivo() {
        archivoElegido = null;
        mostrarArchivoElegido();
    }

    private void volcarEtiquetas(EtiquetasAudioService.Etiquetas etiquetas) {
        rellenarSiVacio(campoTitulo, etiquetas.titulo());
        rellenarSiVacio(campoArtista, etiquetas.artista());
        rellenarSiVacio(campoAlbum, etiquetas.album());
        rellenarSiVacio(campoGenero, etiquetas.genero());
        if (etiquetas.anio() > 0) {
            rellenarSiVacio(campoAnio, String.valueOf(etiquetas.anio()));
        }
        // La duracion del archivo manda siempre: es la real, medida sobre el audio.
        if (etiquetas.duracionSegundos() > 0) {
            campoDuracion.setText(formatearDuracion(etiquetas.duracionSegundos()));
        }
        buscarUriSpotifyEnSegundoPlano();
    }

    private void mostrarArchivoElegido() {
        boolean hay = archivoElegido != null;
        etiquetaArchivo.setText(hay
                ? archivoElegido.getFileName().toString().toUpperCase()
                : "SIN ARCHIVO");
        botonQuitarArchivo.setDisable(!hay);
    }

    // ------------------------------------------------------------------
    // Via B: busqueda en la API
    // ------------------------------------------------------------------

    @FXML
    private void buscarEnLinea() {
        String consulta = textoDe(campoConsulta);
        if (consulta.isBlank()) {
            mostrarError("Escribí algo para buscar, por ejemplo el artista y el título.");
            return;
        }
        limpiarError();
        mostrarEstado("BUSCANDO EN LÍNEA...");
        botonBuscarApi.setDisable(true);

        Task<List<ResultadoBusquedaApi>> tarea = new Task<>() {
            @Override
            protected List<ResultadoBusquedaApi> call() {
                return api.buscar(consulta);
            }
        };

        tarea.setOnSucceeded(evento -> {
            botonBuscarApi.setDisable(false);
            List<ResultadoBusquedaApi> encontrados = tarea.getValue();
            resultados.setAll(encontrados);
            mostrarLista(!encontrados.isEmpty());

            if (!encontrados.isEmpty()) {
                mostrarEstado(encontrados.size() + " RESULTADOS ("
                        + encontrados.get(0).fuente().toUpperCase() + "). ELEGÍ UNO PARA AUTOCOMPLETAR.");
            } else {
                mostrarEstado(api.ultimoAviso()
                        .orElse("Sin resultados. Podés completar los datos a mano."));
            }
        });

        tarea.setOnFailed(evento -> {
            botonBuscarApi.setDisable(false);
            mostrarLista(false);
            mostrarEstado("La búsqueda falló. Podés completar los datos a mano.");
        });

        Thread hilo = new Thread(tarea, "busqueda-metadata");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void configurarListaDeResultados() {
        listaResultados.setItems(resultados);
        listaResultados.setCellFactory(vista -> new CeldaResultado());
        listaResultados.getSelectionModel().selectedItemProperty()
                .addListener((observable, anterior, elegido) -> {
                    if (elegido != null) {
                        aplicarResultado(elegido);
                    }
                });

        // Tercera via: quien escribe titulo y artista a mano, sin usar la busqueda ni un archivo.
        // Se dispara al salir del campo, no en cada tecla, para no consultar a media palabra.
        campoArtista.focusedProperty().addListener((observable, tenia, tiene) -> {
            if (!tiene) {
                buscarUriSpotifyEnSegundoPlano();
            }
        });
    }

    /** Vuelca un resultado de la API sobre el formulario. */
    private void aplicarResultado(ResultadoBusquedaApi elegido) {
        campoTitulo.setText(elegido.titulo());
        campoArtista.setText(nuloAVacio(elegido.artista()));
        campoAlbum.setText(nuloAVacio(elegido.album()));
        campoGenero.setText(nuloAVacio(elegido.genero()));
        campoAnio.setText(elegido.anio() > 0 ? String.valueOf(elegido.anio()) : "");

        // Regla del enunciado: si hay archivo local, su duracion real manda sobre la de la API.
        boolean duracionYaMedida = archivoElegido != null && !textoDe(campoDuracion).isBlank();
        if (!duracionYaMedida && elegido.duracionSegundos() > 0) {
            campoDuracion.setText(formatearDuracion(elegido.duracionSegundos()));
        }

        urlPortadaElegida = elegido.urlPortadaGrande();
        previsualizarPortada(urlPortadaElegida, elegido.fuente());
        limpiarError();

        buscarUriSpotifyEnSegundoPlano();
    }

    /**
     * Busca sola la URI de Spotify a partir del titulo y el interprete del formulario.
     *
     * <p>Pegar la URI a mano exige ir a Spotify, buscar la cancion y usar el menu Compartir por
     * cada una: inviable. Como iTunes ya dejo el titulo y el interprete correctos, esa misma
     * informacion sirve para encontrarla en el catalogo de Spotify.</p>
     *
     * <p>No se toca el campo si el usuario ya escribio algo: lo suyo manda. Y si Spotify no esta
     * configurado, la busqueda simplemente no encuentra nada y el formulario sigue igual — no se
     * muestra ningun error, porque no haberlo configurado no es un fallo.</p>
     */
    private void buscarUriSpotifyEnSegundoPlano() {
        if (!textoDe(campoUriSpotify).isBlank()) {
            return;
        }
        String titulo = textoDe(campoTitulo);
        String artista = textoDe(campoArtista);
        if (titulo.isBlank()) {
            return;
        }

        Task<String> busqueda = new Task<>() {
            @Override
            protected String call() {
                return BuscadorSpotify.buscarUri(titulo, artista).orElse(null);
            }
        };
        // Solo se escribe si el campo sigue vacio: entre que arranco la busqueda y termino, el
        // usuario pudo haber pegado la suya.
        busqueda.setOnSucceeded(evento -> {
            String encontrada = busqueda.getValue();
            if (encontrada != null && textoDe(campoUriSpotify).isBlank()) {
                campoUriSpotify.setText(encontrada);
            }
        });

        Thread hilo = new Thread(busqueda, "buscar-uri-spotify");
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Celda de la lista de resultados: miniatura, titulo, artista y detalle. */
    private final class CeldaResultado extends ListCell<ResultadoBusquedaApi> {

        private final ImageView miniatura = new ImageView();
        private final Label titulo = new Label();
        private final Label detalle = new Label();
        private final HBox contenido = new HBox();

        private CeldaResultado() {
            miniatura.setFitWidth(LADO_MINIATURA);
            miniatura.setFitHeight(LADO_MINIATURA);
            miniatura.setPreserveRatio(true);
            miniatura.setSmooth(false);
            titulo.getStyleClass().add("resultado-titulo");
            detalle.getStyleClass().add("resultado-detalle");

            VBox textos = new VBox(titulo, detalle);
            textos.getStyleClass().add("resultado-textos");
            HBox.setHgrow(textos, Priority.ALWAYS);
            contenido.getChildren().addAll(miniatura, textos);
            contenido.getStyleClass().add("resultado-celda");
            contenido.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(ResultadoBusquedaApi item, boolean vacia) {
            super.updateItem(item, vacia);
            if (vacia || item == null) {
                setGraphic(null);
                return;
            }
            titulo.setText(item.titulo().toUpperCase());
            detalle.setText(nuloAVacio(item.artista()).toUpperCase() + "  •  " + item.detalle());
            miniatura.setImage(imagenRemota(item.urlPortadaMiniatura()));
            setGraphic(contenido);
        }

        /** Carga en segundo plano para que la lista aparezca al instante, sin esperar imagenes. */
        private Image imagenRemota(String url) {
            if (url == null || url.isBlank()) {
                return portadaPorDefecto;
            }
            return new Image(url, LADO_MINIATURA, LADO_MINIATURA, true, false, true);
        }
    }

    // ------------------------------------------------------------------
    // Portada
    // ------------------------------------------------------------------

    private void cargarPortadaPorDefecto() {
        try (InputStream flujo = getClass().getResourceAsStream(RUTA_PORTADA_PLACEHOLDER)) {
            if (flujo != null) {
                portadaPorDefecto = new Image(flujo);
                vistaPortada.setImage(portadaPorDefecto);
            }
        } catch (IOException excepcion) {
            System.err.println("[AVISO] No se pudo cargar la portada por defecto.");
        }
    }

    private void previsualizarPortada(String url, String fuente) {
        if (url == null || url.isBlank()) {
            vistaPortada.setImage(portadaPorDefecto);
            etiquetaFuente.setText("SIN PORTADA");
            return;
        }
        vistaPortada.setImage(new Image(url, 0, 0, true, false, true));
        etiquetaFuente.setText("PORTADA DE " + fuente.toUpperCase());
    }

    private void mostrarPortadaLocalSiExiste(Cancion cancion) {
        String ruta = cancion.getRutaPortada();
        if (ruta == null || ruta.isBlank()) {
            return;
        }
        File archivo = new File(ruta);
        if (archivo.isFile()) {
            vistaPortada.setImage(new Image(archivo.toURI().toString()));
            etiquetaFuente.setText("PORTADA GUARDADA");
        }
    }

    // ------------------------------------------------------------------
    // Guardar y cancelar
    // ------------------------------------------------------------------

    @FXML
    private void guardar() {
        String titulo = textoDe(campoTitulo);
        if (titulo.isBlank()) {
            mostrarError("El título es obligatorio.");
            campoTitulo.requestFocus();
            return;
        }

        int anio;
        try {
            anio = interpretarAnio(textoDe(campoAnio));
        } catch (IllegalArgumentException error) {
            mostrarError(error.getMessage());
            campoAnio.requestFocus();
            return;
        }

        int duracion;
        try {
            duracion = interpretarDuracion(textoDe(campoDuracion));
        } catch (IllegalArgumentException error) {
            mostrarError(error.getMessage());
            campoDuracion.requestFocus();
            return;
        }

        resultado = new DatosCancion(
                titulo,
                textoDe(campoArtista),
                textoDe(campoAlbum),
                textoDe(campoGenero),
                anio,
                duracion,
                (int) Math.round(deslizadorCalificacion.getValue()),
                favorita,
                archivoElegido == null ? null : archivoElegido.toString(),
                urlPortadaElegida,
                normalizarUriSpotify(textoDe(campoUriSpotify)));
        escenario.close();
    }

    /**
     * Acepta tanto la URI de Spotify como el enlace que da el boton Compartir.
     *
     * <p>Nadie copia a mano un {@code spotify:track:...}: lo que se copia desde la aplicacion de
     * Spotify es {@code https://open.spotify.com/track/ID?si=...}. Traducirlo aqui evita que el
     * usuario tenga que saber que existen dos formatos.</p>
     *
     * @param texto lo que escribio el usuario
     * @return la URI normalizada, o {@code null} si el campo venia vacio
     */
    static String normalizarUriSpotify(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.trim();
        if (limpio.startsWith("spotify:track:")) {
            return limpio;
        }
        int inicioDelId = limpio.indexOf("/track/");
        if (inicioDelId < 0) {
            // No se reconoce el formato: se guarda tal cual y que Spotify lo rechace si no vale.
            return limpio;
        }
        String cola = limpio.substring(inicioDelId + "/track/".length());
        // El enlace de Compartir trae "?si=..." de propina.
        int finDelId = cola.indexOf('?');
        String identificador = finDelId < 0 ? cola : cola.substring(0, finDelId);
        return identificador.isBlank() ? null : "spotify:track:" + identificador;
    }

    @FXML
    private void cancelar() {
        resultado = null;
        escenario.close();
    }

    // ------------------------------------------------------------------
    // Interpretacion de los campos de texto
    // ------------------------------------------------------------------

    /**
     * Convierte el texto del anio en un numero.
     *
     * @param texto texto escrito por el usuario; vacio significa "desconocido"
     * @return el anio, o 0 si no se indico
     * @throws IllegalArgumentException si el texto no es un anio razonable
     */
    static int interpretarAnio(String texto) {
        if (texto.isBlank()) {
            return 0;
        }
        try {
            int anio = Integer.parseInt(texto.trim());
            if (anio < 0 || anio > ANIO_MAXIMO_RAZONABLE) {
                throw new IllegalArgumentException("El año debe estar entre 0 y "
                        + ANIO_MAXIMO_RAZONABLE + ".");
            }
            return anio;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("El año debe ser un número, por ejemplo 1975.");
        }
    }

    /**
     * Convierte el texto de la duracion en segundos.
     *
     * <p>Acepta tanto {@code "5:55"} como un numero suelto de segundos.</p>
     *
     * @param texto texto escrito por el usuario; vacio significa "desconocida"
     * @return la duracion en segundos, o 0 si no se indico
     * @throws IllegalArgumentException si el formato no se entiende
     */
    static int interpretarDuracion(String texto) {
        String limpio = texto.trim();
        if (limpio.isBlank()) {
            return 0;
        }
        try {
            if (!limpio.contains(":")) {
                int segundos = Integer.parseInt(limpio);
                if (segundos < 0) {
                    throw new IllegalArgumentException("La duración no puede ser negativa.");
                }
                return segundos;
            }
            String[] partes = limpio.split(":", 2);
            int minutos = Integer.parseInt(partes[0].trim());
            int segundos = Integer.parseInt(partes[1].trim());
            if (minutos < 0 || segundos < 0 || segundos >= SEGUNDOS_POR_MINUTO) {
                throw new IllegalArgumentException("Duración inválida: los segundos van de 0 a 59.");
            }
            return minutos * SEGUNDOS_POR_MINUTO + segundos;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException error) {
            throw new IllegalArgumentException(
                    "La duración debe tener el formato M:SS, por ejemplo 5:55.");
        }
    }

    static String formatearDuracion(int segundos) {
        return String.format("%d:%02d", segundos / SEGUNDOS_POR_MINUTO,
                segundos % SEGUNDOS_POR_MINUTO);
    }

    // ------------------------------------------------------------------
    // Utilidades de la vista
    // ------------------------------------------------------------------

    private static String textoDe(TextField campo) {
        return campo.getText() == null ? "" : campo.getText().trim();
    }

    private static String nuloAVacio(String texto) {
        return texto == null ? "" : texto;
    }

    private static void rellenarSiVacio(TextField campo, String valor) {
        if (valor != null && !valor.isBlank() && textoDe(campo).isBlank()) {
            campo.setText(valor);
        }
    }

    private void mostrarLista(boolean visible) {
        listaResultados.setVisible(visible);
        listaResultados.setManaged(visible);
        escenario.sizeToScene();
    }

    private void mostrarEstado(String mensaje) {
        etiquetaEstadoBusqueda.setText(mensaje);
        etiquetaEstadoBusqueda.setVisible(true);
        etiquetaEstadoBusqueda.setManaged(true);
    }

    private void mostrarError(String mensaje) {
        etiquetaError.setText("• " + mensaje);
        etiquetaError.setVisible(true);
        etiquetaError.setManaged(true);
    }

    private void limpiarError() {
        etiquetaError.setVisible(false);
        etiquetaError.setManaged(false);
    }
}
