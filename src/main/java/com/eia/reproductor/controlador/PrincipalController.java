package com.eia.reproductor.controlador;

import com.eia.reproductor.animacion.AjustesAnimacion;
import com.eia.reproductor.animacion.BarraVolumen;
import com.eia.reproductor.animacion.BarrasSonido;
import com.eia.reproductor.animacion.CapaSpidey;
import com.eia.reproductor.animacion.SpriteAnimado;
import com.eia.reproductor.animacion.VisualizadorEstructura;
import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.ColeccionDeCanciones;
import com.eia.reproductor.modelo.Playlist;
import com.eia.reproductor.modelo.ResultadoBusquedaApi;
import com.eia.reproductor.modos.ModoAlfabetico;
import com.eia.reproductor.modos.ModoAleatorio;
import com.eia.reproductor.modos.ModoOrdenLlegada;
import com.eia.reproductor.modos.ModoReproduccion;
import com.eia.reproductor.servicios.BibliotecaService;
import com.eia.reproductor.servicios.ColeccionBiblioteca;
import com.eia.reproductor.servicios.ColeccionFavoritas;
import com.eia.reproductor.servicios.ColeccionHistorial;
import com.eia.reproductor.servicios.ColeccionPlaylist;
import com.eia.reproductor.servicios.EstadisticasBiblioteca;
import com.eia.reproductor.servicios.FabricaAudio;
import com.eia.reproductor.servicios.FiltroDeCampo;
import com.eia.reproductor.servicios.PlaylistService;
import com.eia.reproductor.servicios.MetadataApiService;
import com.eia.reproductor.servicios.ObservadorBiblioteca;
import com.eia.reproductor.servicios.PersistenciaService;
import com.eia.reproductor.servicios.PortadaService;
import com.eia.reproductor.servicios.ReproductorAudio;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Bounds;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.fxml.Initializable;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

/** Controlador de la ventana principal. */
public class PrincipalController implements Initializable, ObservadorBiblioteca {
    /** Cantidad de bloques de la barra de progreso segmentada. */
    private static final int BLOQUES_PROGRESO = 28;

    private static final double ANCHO_BLOQUE = 8;
    private static final double ALTO_BLOQUE = 12;

    /** Lado de las miniaturas de caratula en la tabla de la biblioteca. */
    private static final double LADO_MINIATURA_TABLA = 30;

    /** Lado de las caratulas en la lista de proximas canciones. */
    private static final double LADO_MINIATURA_COLA = 26;

    /** Duracion que se asume cuando una cancion no declara la suya. */
    private static final int DURACION_SIMULADA_POR_DEFECTO = 180;

    private static final String RUTA_PORTADA_PLACEHOLDER = "/imagenes/portada-placeholder.png";
    private static final String RUTA_TITULO = "/imagenes/spidey/titulo.png";
    private static final String RUTA_INSIGNIA = "/imagenes/spidey/insignia-arania.png";
    private static final String RUTA_MARCO_INSIGNIA = "/imagenes/spidey/contenedor.png";
    private static final String RUTA_FONDO_ARANIA = "/imagenes/spidey/fondo-arania.png";
    /**
     * Titulo de la barra de la ventana de estadisticas, en un solo sitio porque se usa al abrirla y
     * al refrescarla si ya estaba abierta.
     */
    private static final String TITULO_ESTADISTICAS = "ESTADÍSTICAS";
    private static final String CLASE_PESTANIA_ACTIVA = "activa";
    private static final String CLASE_TEMA_CLARO = "tema-claro";
    private static final String CLASE_CELDA_EN_CURSO = "celda-en-curso";

    /** Bloque blanco que marca la posicion exacta dentro de la cancion. */
    private static final String CLASE_CURSOR_PROGRESO = "bloque-progreso-cursor";

    /** Ancho que se le descuenta a la celda: la barra vertical mas el borde de la lista. */
    private static final double ANCHO_BARRA_DESPLAZAMIENTO = 28;

    /** Cuanto salta la reproduccion con las flechas del teclado. */
    private static final long SALTO_TECLADO_MS = 5_000;

    private static final double PASO_MARQUESINA = 2;
    private static final Duration INTERVALO_MARQUESINA = Duration.millis(35);

    // --- Nodos de la vista ---

    @FXML private HBox selectorModos;
    @FXML private HBox ranuraVolumen;
    @FXML private ComboBox<ColeccionDeCanciones> selectorColeccion;
    @FXML private Button botonNuevaLista;
    @FXML private Button botonRenombrarLista;
    @FXML private Button botonBorrarLista;
    @FXML private Button botonFuenteAudio;
    @FXML private Label etiquetaFuenteAudio;
    @FXML private Button botonModoAleatorio;
    @FXML private Button botonModoLlegada;
    @FXML private Button botonModoAlfabetico;

    /** Buscador. */
    @FXML private ComboBox<String> campoBusqueda;

    /** Sobre que campo se busca: TODO, TITULO, ARTISTA, ALBUM o GENERO. */
    @FXML private ComboBox<String> selectorCampo;
    @FXML private Label etiquetaContador;
    @FXML private Label etiquetaEstructuraActiva;
    @FXML private Label etiquetaAviso;

    @FXML private TableView<Cancion> tablaBiblioteca;
    @FXML private TableColumn<Cancion, Cancion> columnaPortada;
    @FXML private TableColumn<Cancion, String> columnaTitulo;
    @FXML private TableColumn<Cancion, String> columnaArtista;
    @FXML private TableColumn<Cancion, String> columnaAlbum;
    @FXML private TableColumn<Cancion, String> columnaGenero;
    @FXML private TableColumn<Cancion, String> columnaAnio;
    @FXML private TableColumn<Cancion, String> columnaDuracion;
    @FXML private TableColumn<Cancion, String> columnaCalificacion;
    @FXML private TableColumn<Cancion, String> columnaReproducciones;

    @FXML private Button botonEditar;
    @FXML private Button botonEliminar;

    @FXML private ImageView imagenPortada;
    @FXML private Label etiquetaTitulo;
    @FXML private Label etiquetaArtista;
    @FXML private Label etiquetaDetalle;

    @FXML private HBox barraProgreso;
    @FXML private Label etiquetaTiempoActual;
    @FXML private Label etiquetaTiempoTotal;

    @FXML private Button botonAnterior;
    @FXML private Button botonReproducir;
    @FXML private ImageView iconoReproducir;
    @FXML private Button botonSiguiente;
    @FXML private Button botonAccionModo;
    @FXML private HBox ranuraBarras;

    @FXML private ListView<Cancion> listaSiguientes;
    @FXML private HBox contenedorMarquesina;
    @FXML private Label etiquetaMarquesina;

    @FXML private Button botonTema;
    @FXML private HBox barraTitulo;
    @FXML private VBox cabecera;
    @FXML private Pane capaAnimaciones;
    @FXML private ImageView imagenTitulo;
    @FXML private ImageView fondoArania;
    @FXML private ImageView insigniaDerecha;
    @FXML private StackPane insigniaIzquierda;

    // --- Estado y colaboradores ---

    private final PersistenciaService persistencia = new PersistenciaService();
    private final BibliotecaService biblioteca = new BibliotecaService(persistencia);
    private final PortadaService portadas = new PortadaService();
    private final MetadataApiService api = new MetadataApiService();

    /** Caratulas ya leidas del disco, para no releerlas en cada refresco. */
    private final Map<String, Image> cachePortadas = new HashMap<>();

    private final ModoAleatorio modoAleatorio = new ModoAleatorio();
    private final ModoOrdenLlegada modoOrdenLlegada = new ModoOrdenLlegada();
    private final ModoAlfabetico modoAlfabetico = new ModoAlfabetico();
    private ModoReproduccion modoActivo;

    private final ObservableList<Cancion> filasBiblioteca = FXCollections.observableArrayList();
    private final ObservableList<Cancion> filasSiguientes = FXCollections.observableArrayList();
    private final List<Rectangle> bloquesProgreso = new ArrayList<>();

    private final CapaSpidey capaSpidey = new CapaSpidey();
    private final BarrasSonido barrasSonido = new BarrasSonido();
    private final BarraVolumen barraVolumen = new BarraVolumen();
    private final VisualizadorEstructura visualizador = new VisualizadorEstructura();

    /** Ventana del visualizador; {@code null} mientras esta cerrada. */
    private Stage ventanaEstructura;

    /** Ventana de estadisticas; {@code null} mientras esta cerrada. */
    private Stage ventanaEstadisticas;
    private Image iconoPlay;
    private Image iconoPausa;

    /** Fuente de audio. */
    private final ReproductorAudio audio = FabricaAudio.crear();

    private final PlaylistService listas = new PlaylistService();

    /** Coleccion que se esta reproduciendo y mostrando. */
    private ColeccionDeCanciones coleccionActiva;

    private Timeline animacionMarquesina;
    private Image portadaPorDefecto;
    /** Evita que reconstruir el selector se confunda con que el usuario eligio otra coleccion. */
    private boolean reconstruyendoSelector;

    /** Menu del clic derecho; se guarda para poder cerrarlo antes de abrir el siguiente. */
    private ContextMenu menuDeCanciones;

    /** Posicion y tamanio previos al agrandar; {@code null} cuando la ventana esta normal. */
    private Rectangle2D tamanioAnterior;

    /** Verdadero mientras el usuario tiene el raton pulsado sobre la barra de progreso. */
    private boolean arrastrandoProgreso;

    /** Fraccion de la pista bajo el cursor durante el arrastre, entre 0 y 1. */
    private double avanceArrastrado;

    private double desplazamientoArrastreX;
    private double desplazamientoArrastreY;

    // --- Arranque ---

    @Override
    public void initialize(URL ubicacion, ResourceBundle recursos) {
        configurarTabla();
        configurarListaSiguientes();
        construirBarraDeProgreso();
        conectarAudio();
        cargarPortadaPorDefecto();

        boolean primerArranque = !persistencia.existeArchivo();
        biblioteca.cargarDesdeDisco();
        if (primerArranque && biblioteca.estaVacia()) {
            sembrarCancionesDeEjemplo();
        }
        biblioteca.ultimoAviso().ifPresent(this::mostrarAviso);

        // El observador se registra despues de la carga inicial para no notificar a un modo que
        // todavia no existe.
        biblioteca.registrarObservador(this);

        // Las listas se cargan despues de la biblioteca: apuntan a sus canciones por id, asi que
        // limpiar las huerfanas necesita que la biblioteca ya este en memoria.
        listas.cargarDesdeDisco();
        listas.limpiarHuerfanas(biblioteca);
        coleccionActiva = new ColeccionBiblioteca(biblioteca);
        // El selector de campo se llena aqui y no en configurarTabla porque setValue dispara su
        // onAction, y ese handler repinta la tabla: antes de esta linea coleccionActiva es null.
        selectorCampo.getItems().setAll(FiltroDeCampo.etiquetas());
        selectorCampo.setValue(FiltroDeCampo.TODO.etiqueta());
        configurarSelectorDeColecciones();
        refrescarSelectorDeColecciones();
        configurarMenuDeCanciones();

        instalarAtajos();
        activarModo(modoAleatorio);
        prepararMarquesina();
        prepararBarraTitulo();
        prepararAdornosSpidey();
        refrescarTabla();
    }

    /** Instala los atajos de teclado. */
    private void instalarAtajos() {
        tablaBiblioteca.sceneProperty().addListener((observable, sinEscena, escena) -> {
            if (escena != null) {
                escena.addEventFilter(KeyEvent.KEY_PRESSED, this::atenderTecla);
            }
        });
    }

    /** Traduce una tecla a una accion del reproductor. */
    private void atenderTecla(KeyEvent evento) {
        // Mientras se escribe, el teclado es del campo de texto y de nadie mas. Los desplegables
        // tambien quedan fuera: en ellos el espacio abre la lista y las flechas mueven la
        if (evento.getTarget() instanceof TextField
                || evento.getTarget() instanceof ComboBox<?>) {
            return;
        }
        boolean conControl = evento.isControlDown();
        switch (evento.getCode()) {
            case SPACE -> consumir(evento, this::alternarReproduccion);
            case RIGHT -> consumir(evento, conControl
                    ? this::siguiente
                    : () -> audio.avanzarRelativo(SALTO_TECLADO_MS));
            case LEFT -> consumir(evento, conControl
                    ? this::anterior
                    : () -> audio.avanzarRelativo(-SALTO_TECLADO_MS));
            case N -> {
                if (conControl) {
                    consumir(evento, this::agregarCancion);
                }
            }
            case F -> {
                if (conControl) {
                    // El editor, no el ComboBox: enfocar el control deja el cursor sin aparecer
                    // y hay que hacer clic dentro para poder escribir.
                    consumir(evento, campoBusqueda.getEditor()::requestFocus);
                }
            }
            case D -> {
                if (conControl) {
                    consumir(evento, this::alternarTema);
                }
            }
            default -> { }
        }
    }

    /** Ejecuta la accion y marca la tecla como atendida, para que nadie mas reaccione a ella. */
    private static void consumir(KeyEvent evento, Runnable accion) {
        accion.run();
        evento.consume();
    }

    /** Ata la interfaz al servicio de audio. */
    private void conectarAudio() {
        audio.posicionMsProperty().addListener((observable, anterior, actual) -> refrescarProgreso());
        audio.duracionMsProperty().addListener((observable, anterior, actual) -> refrescarProgreso());
        audio.reproduciendoProperty().addListener(
                (observable, anterior, actual) -> actualizarBotonesDeTransporte());
        audio.setAlTerminarPista(() -> Platform.runLater(this::siguiente));

        // La barra de volumen no conoce el audio: avisa del nivel y aqui se traduce a la orden.
        ranuraVolumen.getChildren().add(barraVolumen.nodo());
        barraVolumen.setAlCambiar(audio::setVolumen);
        audio.setVolumen(barraVolumen.volumen());

        // El ecualizador se mueve con el espectro real cuando la fuente puede darlo. El analisis
        // llega desde el hilo del audio, asi que se pasa al de la interfaz antes de tocar nada.
        audio.setAlAnalizarEspectro(
                niveles -> Platform.runLater(() -> barrasSonido.mostrarNiveles(niveles)),
                BarrasSonido.BANDAS);

        // Un MP3 roto no revienta al abrirlo sino al decodificarlo, en otro hilo: por eso el fallo
        // llega como aviso y se muestra en la misma franja que el resto de las advertencias.
        audio.setAlFallar(mensaje -> Platform.runLater(() -> mostrarAviso(mensaje)));
    }

    /** Monta la cabecera ilustrada y la capa de animaciones. */
    private void prepararAdornosSpidey() {
        cargarImagen(RUTA_TITULO).ifPresent(imagenTitulo::setImage);
        cargarImagen(RUTA_INSIGNIA).ifPresent(insigniaDerecha::setImage);
        cargarImagen(RUTA_FONDO_ARANIA).ifPresent(fondoArania::setImage);

        iconoPlay = cargarImagen(AjustesAnimacion.RUTA_BOTON_PLAY).orElse(null);
        iconoPausa = cargarImagen(AjustesAnimacion.RUTA_BOTON_PAUSA).orElse(null);
        iconoReproducir.setImage(iconoPlay);
        ranuraBarras.getChildren().add(barrasSonido.nodo());

        // Insignia animada de la izquierda. Va sin el marco octogonal a proposito: sumarlo
        // engordaba la cabecera 30 px y esos 30 px se los quitaba a la lista de proximas.
        SpriteAnimado senial = new SpriteAnimado(
                AjustesAnimacion.RUTA_SENIAL, AjustesAnimacion.SENIAL_CUADROS,
                AjustesAnimacion.SENIAL_ANCHO_CUADRO, AjustesAnimacion.SENIAL_ALTO_CUADRO,
                AjustesAnimacion.ESCALA, AjustesAnimacion.SENIAL_FPS);
        insigniaIzquierda.getChildren().add(senial.nodo());
        senial.iniciar();

        capaAnimaciones.getChildren().add(capaSpidey.nodo());
        capaSpidey.nodo().prefWidthProperty().bind(capaAnimaciones.widthProperty());
        capaSpidey.nodo().prefHeightProperty().bind(capaAnimaciones.heightProperty());
        // La telarania nace en el borde inferior de las pestanias de modo. No basta con la altura
        // de la cabecera: la capa de animacion cubre tambien el marco exterior, asi que esa altura
        capaSpidey.origenTelaranaProperty().bind(Bindings.createDoubleBinding(
                () -> {
                    Bounds enEscena = selectorModos.localToScene(selectorModos.getBoundsInLocal());
                    return capaAnimaciones.sceneToLocal(0, enEscena.getMaxY()).getY();
                },
                selectorModos.boundsInParentProperty(),
                capaAnimaciones.boundsInParentProperty(),
                cabecera.heightProperty()));
        // La aplicacion arranca en modo claro, asi que el traje de salida es el rojo. El FXML ya
        // trae puesta la clase "tema-claro" en la raiz; esto solo pone el traje que le toca.
        capaSpidey.usarTrajeNegro(false);
        capaSpidey.iniciar();
    }

    private static Optional<Image> cargarImagen(String ruta) {
        try (InputStream flujo = PrincipalController.class.getResourceAsStream(ruta)) {
            return flujo == null ? Optional.empty() : Optional.of(new Image(flujo));
        } catch (Exception excepcion) {
            System.err.println("[AVISO] No se pudo cargar " + ruta + ": " + excepcion.getMessage());
            return Optional.empty();
        }
    }

    /** Carga unas canciones de muestra la primera vez que se abre la aplicacion. */
    private void sembrarCancionesDeEjemplo() {
        List<Cancion> ejemplos = List.of(
                agregarEjemplo("Bohemian Rhapsody", "Queen", "A Night at the Opera", "Rock", 1975, 355, 98),
                agregarEjemplo("Africa", "Toto", "Toto IV", "Pop", 1982, 295, 90),
                agregarEjemplo("Creep", "Radiohead", "Pablo Honey", "Rock alternativo", 1993, 238, 85),
                agregarEjemplo("Zombie", "The Cranberries", "No Need to Argue", "Rock", 1994, 306, 88),
                agregarEjemplo("Ángel", "Robbie Williams", "Life thru a Lens", "Pop", 1997, 260, 70),
                agregarEjemplo("Enter Sandman", "Metallica", "Metallica", "Metal", 1991, 331, 92),
                agregarEjemplo("Dream On", "Aerosmith", "Aerosmith", "Rock", 1973, 268, 80));

        mostrarAviso("Se cargaron " + ejemplos.size() + " canciones de ejemplo. "
                + "Buscando sus carátulas en línea...");
        buscarPortadasEnSegundoPlano(ejemplos);
    }

    private Cancion agregarEjemplo(String titulo, String artista, String album, String genero,
                                   int anio, int duracion, int calificacion) {
        Cancion cancion = new Cancion(titulo);
        cancion.setArtista(artista);
        cancion.setAlbum(album);
        cancion.setGenero(genero);
        cancion.setAnio(anio);
        cancion.setDuracionSegundos(duracion);
        cancion.setCalificacion(calificacion);
        biblioteca.agregar(cancion);
        return cancion;
    }

    /** Busca en la API la caratula de cada cancion y la descarga, sin bloquear la interfaz. */
    private void buscarPortadasEnSegundoPlano(List<Cancion> canciones) {
        Task<Integer> tarea = new Task<>() {
            @Override
            protected Integer call() {
                int encontradas = 0;
                for (Cancion cancion : canciones) {
                    if (isCancelled()) {
                        break;
                    }
                    if (buscarYAplicarPortada(cancion)) {
                        encontradas++;
                    }
                }
                return encontradas;
            }
        };

        tarea.setOnSucceeded(evento -> {
            int encontradas = tarea.getValue();
            if (encontradas > 0) {
                mostrarAviso("Listo: " + encontradas + " de " + canciones.size()
                        + " carátulas descargadas. Podés eliminar los ejemplos y agregar tu música.");
            } else {
                mostrarAviso("No se pudieron descargar las carátulas (¿sin internet?). "
                        + "Las canciones de ejemplo funcionan igual.");
            }
        });
        tarea.setOnFailed(evento -> mostrarAviso(
                "Las canciones de ejemplo están cargadas, pero no se pudieron traer las carátulas."));

        Thread hilo = new Thread(tarea, "portadas-ejemplo");
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Consulta la API por una cancion, descarga su caratula y la asocia. */
    private boolean buscarYAplicarPortada(Cancion cancion) {
        List<ResultadoBusquedaApi> resultados =
                api.buscar(cancion.getArtista() + " " + cancion.getTitulo());
        if (resultados.isEmpty()) {
            return false;
        }

        ResultadoBusquedaApi elegido = mejorCoincidencia(resultados, cancion.getTitulo());
        String url = elegido.urlPortadaGrande();
        if (url == null || url.isBlank()) {
            return false;
        }

        Optional<Path> archivo = portadas.obtener(cancion.getId(), url);
        if (archivo.isEmpty()) {
            return false;
        }

        Platform.runLater(() -> {
            // Se limpia la cache ANTES de editar: al editar se refresca la tabla, y si la entrada
            // vieja siguiera en memoria la celda volveria a pintar la imagen de reemplazo.
            cachePortadas.remove(cancion.getId());
            biblioteca.editar(cancion, objetivo -> {
                objetivo.setUrlPortadaRemota(url);
                objetivo.setRutaPortada(archivo.get().toString());
            });
            refrescarReproductor();
        });
        return true;
    }

    /** Elige el resultado cuyo titulo coincide exactamente, si lo hay. */
    private static ResultadoBusquedaApi mejorCoincidencia(List<ResultadoBusquedaApi> resultados,
                                                          String tituloBuscado) {
        for (ResultadoBusquedaApi candidato : resultados) {
            if (candidato.titulo() != null
                    && candidato.titulo().equalsIgnoreCase(tituloBuscado)
                    && candidato.urlPortadaGrande() != null) {
                return candidato;
            }
        }
        return resultados.get(0);
    }

    // --- Configuracion de los controles ---

    private void configurarTabla() {
        configurarColumnaDePortada();

        // Se usan lambdas en vez de PropertyValueFactory: no hay reflexion ni cadenas magicas,
        // y si se renombra un getter el compilador avisa.
        columnaTitulo.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().getTitulo()));
        columnaArtista.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().getArtista()));
        columnaAlbum.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().getAlbum()));
        columnaGenero.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().getGenero()));
        columnaAnio.setCellValueFactory(f -> new SimpleStringProperty(textoAnio(f.getValue())));
        columnaDuracion.setCellValueFactory(
                f -> new SimpleStringProperty(f.getValue().duracionFormateada()));
        columnaCalificacion.setCellValueFactory(
                f -> new SimpleStringProperty(textoCalificacion(f.getValue())));
        // Un guion en vez de un cero: una columna llena de ceros distrae y lo que interesa ver de
        // un vistazo es cuales si han sonado.
        columnaReproducciones.setCellValueFactory(f -> {
            int veces = f.getValue().getVecesReproducida();
            return new SimpleStringProperty(veces == 0 ? "—" : String.valueOf(veces));
        });

        // Las columnas se reparten el ancho disponible en vez de desbordarse con barra horizontal.
        tablaBiblioteca.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaBiblioteca.setItems(filasBiblioteca);
        tablaBiblioteca.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tablaBiblioteca.getSelectionModel().selectedItemProperty()
                .addListener((observable, anterior, actual) -> actualizarBotonesDeSeleccion());
        tablaBiblioteca.setPlaceholder(new Label("LA BIBLIOTECA ESTÁ VACÍA"));

        // Se escucha el editor y no valueProperty: al escribir a mano el value no cambia hasta que
        // se pulsa Enter, y la tabla debe ir filtrando letra a letra como hacia antes.
        campoBusqueda.getEditor().textProperty()
                .addListener((observable, anterior, actual) -> refrescarTabla());
        actualizarBotonesDeSeleccion();
    }

    /** Cambia el campo sobre el que se filtra y rellena el desplegable con sus valores reales. */
    @FXML
    private void cambiarCampoDeFiltro() {
        FiltroDeCampo campo = campoDeFiltro();
        campoBusqueda.getItems().setAll(campo.valoresEn(biblioteca.todas()));
        campoBusqueda.setPromptText(campo == FiltroDeCampo.TODO
                ? "BUSCAR TÍTULO, ARTISTA O ÁLBUM"
                : "FILTRAR POR " + campo.etiqueta());
        refrescarTabla();
    }

    /** @return el campo elegido en el selector, o TODO si todavia no hay ninguno */
    private FiltroDeCampo campoDeFiltro() {
        return FiltroDeCampo.porEtiqueta(selectorCampo.getValue());
    }

    /** Prepara la columna de miniaturas de la tabla. */
    private void configurarColumnaDePortada() {
        columnaPortada.setCellValueFactory(fila -> new SimpleObjectProperty<>(fila.getValue()));
        columnaPortada.setCellFactory(columna -> new TableCell<>() {
            private final ImageView miniatura = new ImageView();

            {
                miniatura.setFitWidth(LADO_MINIATURA_TABLA);
                miniatura.setFitHeight(LADO_MINIATURA_TABLA);
                miniatura.setPreserveRatio(true);
                miniatura.setSmooth(false);
            }

            @Override
            protected void updateItem(Cancion cancion, boolean vacia) {
                super.updateItem(cancion, vacia);
                if (vacia || cancion == null) {
                    setGraphic(null);
                    return;
                }
                miniatura.setImage(portadaDe(cancion));
                setGraphic(miniatura);
            }
        });
    }

    private void configurarListaSiguientes() {
        listaSiguientes.setItems(filasSiguientes);
        listaSiguientes.setFocusTraversable(false);
        listaSiguientes.setPlaceholder(new Label("NADA EN COLA"));
        // Cada fila lleva su caratula, el titulo y el artista en dos renglones: con una sola linea
        // de texto los titulos largos se cortaban y no se distinguia una cancion de otra.
        listaSiguientes.setCellFactory(vista -> new ListCell<>() {
            private final ImageView miniatura = new ImageView();
            private final Label numeroYTitulo = new Label();
            private final Label artista = new Label();
            private final HBox contenido = new HBox();

            {
                miniatura.setFitWidth(LADO_MINIATURA_COLA);
                miniatura.setFitHeight(LADO_MINIATURA_COLA);
                miniatura.setPreserveRatio(true);
                miniatura.setSmooth(false);
                numeroYTitulo.getStyleClass().add("cola-titulo");
                artista.getStyleClass().add("cola-artista");

                VBox textos = new VBox(numeroYTitulo, artista);
                textos.getStyleClass().add("cola-textos");
                HBox.setHgrow(textos, javafx.scene.layout.Priority.ALWAYS);
                contenido.getChildren().addAll(miniatura, textos);
                contenido.getStyleClass().add("cola-celda");
                contenido.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                // La celda se ata al ancho visible de la lista. Sin esto, un titulo largo hacia
                // la celda mas ancha que la lista, aparecia una barra de desplazamiento
                contenido.maxWidthProperty().bind(
                        listaSiguientes.widthProperty().subtract(ANCHO_BARRA_DESPLAZAMIENTO));
                contenido.prefWidthProperty().bind(contenido.maxWidthProperty());
            }

            @Override
            protected void updateItem(Cancion cancion, boolean vacia) {
                super.updateItem(cancion, vacia);
                getStyleClass().remove(CLASE_CELDA_EN_CURSO);
                if (vacia || cancion == null) {
                    setGraphic(null);
                    return;
                }
                numeroYTitulo.setText((getIndex() + 1) + ". " + cancion.getTitulo().toUpperCase());
                artista.setText(cancion.getArtista().toUpperCase()
                        + "  •  " + cancion.duracionFormateada());
                miniatura.setImage(portadaDe(cancion));
                setGraphic(contenido);
                if (cancion.equals(modoActivo == null ? null : modoActivo.actual())) {
                    getStyleClass().add(CLASE_CELDA_EN_CURSO);
                }
            }
        });
    }

    /** Crea los rectangulos de la barra de progreso y le da el comportamiento de arrastre. */
    private void construirBarraDeProgreso() {
        for (int i = 0; i < BLOQUES_PROGRESO; i++) {
            Rectangle bloque = new Rectangle(ANCHO_BLOQUE, ALTO_BLOQUE);
            bloque.getStyleClass().add("bloque-progreso");
            bloquesProgreso.add(bloque);
            barraProgreso.getChildren().add(bloque);
        }

        barraProgreso.setCursor(Cursor.HAND);
        barraProgreso.setOnMousePressed(evento -> {
            if (modoActivo == null || modoActivo.actual() == null) {
                return;
            }
            arrastrandoProgreso = true;
            avanceArrastrado = fraccionEn(evento.getX());
            refrescarProgreso();
        });
        barraProgreso.setOnMouseDragged(evento -> {
            if (!arrastrandoProgreso) {
                return;
            }
            avanceArrastrado = fraccionEn(evento.getX());
            refrescarProgreso();
        });
        barraProgreso.setOnMouseReleased(evento -> {
            if (!arrastrandoProgreso) {
                return;
            }
            avanceArrastrado = fraccionEn(evento.getX());
            arrastrandoProgreso = false;
            audio.buscarPosicion(Math.round(avanceArrastrado * duracionDeLaPista() * 1000));
            refrescarProgreso();
        });
    }

    /** Traduce una coordenada horizontal dentro de la barra a una fraccion de la pista. */
    private double fraccionEn(double x) {
        double ancho = barraProgreso.getWidth();
        if (ancho <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, x / ancho));
    }

    private void cargarPortadaPorDefecto() {
        try (InputStream flujo = getClass().getResourceAsStream(RUTA_PORTADA_PLACEHOLDER)) {
            if (flujo != null) {
                portadaPorDefecto = new Image(flujo);
                imagenPortada.setImage(portadaPorDefecto);
            }
        } catch (Exception excepcion) {
            System.err.println("[AVISO] No se pudo cargar la portada por defecto: "
                    + excepcion.getMessage());
        }
    }

    private void prepararMarquesina() {
        // Se recorta el contenedor para que el texto desaparezca por los bordes en vez de
        // desbordarse sobre el resto de la ventana.
        Rectangle recorte = new Rectangle();
        recorte.widthProperty().bind(contenedorMarquesina.widthProperty());
        recorte.heightProperty().bind(contenedorMarquesina.heightProperty());
        contenedorMarquesina.setClip(recorte);
        etiquetaMarquesina.setMinWidth(Region.USE_PREF_SIZE);

        animacionMarquesina = new Timeline(new KeyFrame(INTERVALO_MARQUESINA, evento -> {
            double desplazamiento = etiquetaMarquesina.getTranslateX() - PASO_MARQUESINA;
            if (desplazamiento < -etiquetaMarquesina.getWidth()) {
                desplazamiento = contenedorMarquesina.getWidth();
            }
            etiquetaMarquesina.setTranslateX(desplazamiento);
        }));
        animacionMarquesina.setCycleCount(Animation.INDEFINITE);
        animacionMarquesina.play();
    }

    // --- Selector de modo ---

    @FXML
    private void activarModoAleatorio() {
        activarModo(modoAleatorio);
    }

    @FXML
    private void activarModoOrdenLlegada() {
        activarModo(modoOrdenLlegada);
    }

    @FXML
    private void activarModoAlfabetico() {
        activarModo(modoAlfabetico);
    }

    /** Cambia el modo activo. */
    private void activarModo(ModoReproduccion modo) {
        audio.detener();
        modoActivo = modo;
        // Aqui esta el enganche de las listas: el modo recibe la coleccion elegida, no la
        // biblioteca entera. Las estructuras de datos no se enteran de que existen las listas.
        modoActivo.cargar(coleccionActiva.canciones());
        actualizarPestanias();
        refrescarReproductor();
    }

    // --- Listas de reproduccion ---

    /** Enseña al selector a mostrar el nombre de cada coleccion y cuantas canciones tiene. */
    private void configurarSelectorDeColecciones() {
        selectorColeccion.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(ColeccionDeCanciones coleccion) {
                return coleccion == null
                        ? ""
                        : coleccion.nombre() + "  (" + coleccion.tamanio() + ")";
            }

            @Override
            public ColeccionDeCanciones fromString(String texto) {
                // El selector no es editable: nunca se convierte de texto a coleccion.
                return null;
            }
        });
    }

    /** Rellena el selector con la biblioteca, las favoritas y las listas del usuario. */
    private void refrescarSelectorDeColecciones() {
        ColeccionDeCanciones elegida = coleccionActiva;

        List<ColeccionDeCanciones> disponibles = new ArrayList<>();
        disponibles.add(new ColeccionBiblioteca(biblioteca));
        disponibles.add(new ColeccionFavoritas(biblioteca));
        // Se le pasa un proveedor y no el modo: el modo activo cambia y una referencia fija
        // acabaria mostrando el historial del modo anterior.
        disponibles.add(new ColeccionHistorial(() -> modoActivo));
        for (Playlist lista : listas.todas()) {
            disponibles.add(new ColeccionPlaylist(lista, biblioteca));
        }

        // El selector se reconstruye entero, asi que hay que volver a marcar la que estaba puesta
        // buscandola por nombre: los objetos son nuevos.
        selectorColeccion.getItems().setAll(disponibles);
        ColeccionDeCanciones aSeleccionar = disponibles.stream()
                .filter(coleccion -> elegida != null && coleccion.nombre().equals(elegida.nombre()))
                .findFirst()
                .orElse(disponibles.get(0));

        reconstruyendoSelector = true;
        selectorColeccion.getSelectionModel().select(aSeleccionar);
        reconstruyendoSelector = false;
        coleccionActiva = aSeleccionar;

        actualizarBotonesDeLista();
    }

    private void actualizarBotonesDeLista() {
        boolean esPersonal = coleccionActiva instanceof ColeccionPlaylist;
        botonRenombrarLista.setDisable(!esPersonal);
        botonBorrarLista.setDisable(!esPersonal);
    }

    /** Cambia la coleccion que se muestra y se reproduce. */
    @FXML
    private void cambiarColeccion() {
        if (reconstruyendoSelector) {
            return;
        }
        ColeccionDeCanciones elegida = selectorColeccion.getValue();
        if (elegida == null || elegida == coleccionActiva) {
            return;
        }
        coleccionActiva = elegida;
        actualizarBotonesDeLista();
        // Recargar el modo es obligatorio: la estructura de datos tiene dentro las canciones de
        // la coleccion anterior.
        activarModo(modoActivo);
        refrescarTabla();
    }

    /** Crea una lista nueva pidiendo el nombre. */
    @FXML
    private void crearPlaylist() {
        pedirNombre("NUEVA LISTA", "¿Cómo se va a llamar?", "").ifPresent(nombre -> {
            Optional<Playlist> creada = listas.crear(nombre);
            if (creada.isEmpty()) {
                mostrarAviso(listas.ultimoAviso().orElse("No se pudo crear la lista."));
                return;
            }
            coleccionActiva = new ColeccionPlaylist(creada.get(), biblioteca);
            refrescarSelectorDeColecciones();
            activarModo(modoActivo);
            refrescarTabla();
            limpiarAviso();
        });
    }

    /** Renombra la lista que este seleccionada. */
    @FXML
    private void renombrarPlaylist() {
        if (!(coleccionActiva instanceof ColeccionPlaylist actual)) {
            return;
        }
        pedirNombre("RENOMBRAR LISTA", "Nuevo nombre:", actual.nombre()).ifPresent(nombre -> {
            if (!listas.renombrar(actual.playlist(), nombre)) {
                mostrarAviso(listas.ultimoAviso().orElse("No se pudo renombrar la lista."));
                return;
            }
            refrescarSelectorDeColecciones();
            limpiarAviso();
        });
    }

    /** Borra la lista seleccionada, previa confirmacion. */
    @FXML
    private void borrarPlaylist() {
        if (!(coleccionActiva instanceof ColeccionPlaylist actual)) {
            return;
        }
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Borrar la lista \"" + actual.nombre() + "\"?\n"
                        + "Las canciones siguen en la biblioteca.",
                ButtonType.CANCEL, ButtonType.OK);
        confirmacion.setTitle("BORRAR LISTA");
        confirmacion.setHeaderText(null);
        aplicarEstilos(confirmacion);
        confirmacion.initOwner(ventana());

        if (confirmacion.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        listas.eliminar(actual.playlist());
        coleccionActiva = null;
        refrescarSelectorDeColecciones();
        activarModo(modoActivo);
        refrescarTabla();
    }

    /** Pide un nombre al usuario. */
    private Optional<String> pedirNombre(String titulo, String mensaje, String inicial) {
        return DialogoTexto.pedir(ventana(), titulo, mensaje, inicial);
    }

    /** Arma el menu que aparece al hacer clic derecho sobre una cancion de la tabla. */
    private void configurarMenuDeCanciones() {
        // Se construye entero en cada clic derecho, y NO con setContextMenu + setOnShowing:
        // JavaFX no llega a mostrar un menu que empieza vacio, asi que ese enganche no dispara
        tablaBiblioteca.setOnContextMenuRequested(evento -> {
            if (menuDeCanciones != null) {
                menuDeCanciones.hide();
            }
            // Clic derecho sobre una fila la selecciona: sin esto habria que hacer clic izquierdo
            // antes, y el menu saldria referido a otra cancion.
            Cancion sobreLaQueSeHizoClic = tablaBiblioteca.getSelectionModel().getSelectedItem();
            menuDeCanciones = construirMenuPara(sobreLaQueSeHizoClic);
            menuDeCanciones.show(tablaBiblioteca, evento.getScreenX(), evento.getScreenY());
            evento.consume();
        });
    }

    /** Arma el menu del clic derecho para una cancion. */
    private ContextMenu construirMenuPara(Cancion cancion) {
        ContextMenu menu = new ContextMenu();
        if (cancion == null) {
            menu.getItems().add(itemInerte("Hacé clic en una canción primero"));
            return menu;
        }

        MenuItem favorita = new MenuItem(cancion.isFavorita()
                ? "Quitar de ★ FAVORITAS"
                : "Agregar a ★ FAVORITAS");
        favorita.setOnAction(accion -> alternarFavorita(cancion));
        menu.getItems().add(favorita);
        menu.getItems().add(new SeparatorMenuItem());

        if (listas.tamanio() == 0) {
            menu.getItems().add(itemInerte("No hay listas: creá una con NUEVA"));
        }
        for (Playlist lista : listas.todas()) {
            menu.getItems().add(itemDeLista(lista, cancion));
        }
        return menu;
    }

    /** Entrada del menu que mete o saca la cancion de una lista, segun donde este. */
    private MenuItem itemDeLista(Playlist lista, Cancion cancion) {
        boolean yaEsta = lista.contiene(cancion.getId());
        MenuItem item = new MenuItem((yaEsta ? "Quitar de " : "Agregar a ") + lista.getNombre());
        item.setOnAction(accion -> {
            if (yaEsta) {
                listas.quitarCancion(lista, cancion);
            } else if (!listas.agregarCancion(lista, cancion)) {
                mostrarAviso(listas.ultimoAviso().orElse("No se pudo agregar a la lista."));
                return;
            }
            limpiarAviso();
            refrescarSelectorDeColecciones();
            // Si se esta viendo justo esa lista, la tabla y la cola tienen que reflejarlo ya.
            if (coleccionActiva instanceof ColeccionPlaylist mostrada
                    && mostrada.playlist().equals(lista)) {
                activarModo(modoActivo);
                refrescarTabla();
            }
        });
        return item;
    }

    private static MenuItem itemInerte(String texto) {
        MenuItem item = new MenuItem(texto);
        item.setDisable(true);
        return item;
    }

    /** Pone o quita la estrella, que es lo que gobierna la coleccion de favoritas. */
    private void alternarFavorita(Cancion cancion) {
        biblioteca.alternarFavorita(cancion);
        refrescarTabla();
        if (coleccionActiva instanceof ColeccionFavoritas) {
            activarModo(modoActivo);
        }
        refrescarReproductor();
    }

    private void actualizarPestanias() {
        marcarPestania(botonModoAleatorio, modoActivo == modoAleatorio);
        marcarPestania(botonModoLlegada, modoActivo == modoOrdenLlegada);
        marcarPestania(botonModoAlfabetico, modoActivo == modoAlfabetico);
        etiquetaEstructuraActiva.setText("ESTRUCTURA ACTIVA: "
                + modoActivo.estructuraUsada().toUpperCase());
    }

    private static void marcarPestania(Button pestania, boolean activa) {
        pestania.getStyleClass().remove(CLASE_PESTANIA_ACTIVA);
        if (activa) {
            pestania.getStyleClass().add(CLASE_PESTANIA_ACTIVA);
        }
    }

    /** Ejecuta la accion propia del modo activo: volver a mezclar o recargar la cola. */
    @FXML
    private void ejecutarAccionDelModo() {
        if (modoActivo == modoAleatorio) {
            // Si venia sonando algo, la mezcla nueva toma el relevo desde su primera cancion. Si
            // no, se rebaraja en silencio y ya sonara cuando el usuario le de a reproducir.
            boolean sonaba = estaSonando();
            if (modoAleatorio.volverAMezclar() && sonaba) {
                modoAleatorio.siguiente();
                iniciarPista();
                return;
            }
        } else if (modoActivo == modoOrdenLlegada) {
            modoOrdenLlegada.cargar(coleccionActiva.canciones());
            audio.detener();
        } else {
            modoActivo.reiniciar();
            audio.detener();
        }
        refrescarReproductor();
    }

    // --- Transporte ---

    /** Unico boton de reproduccion: arranca si esta detenido y pausa si esta sonando. */
    @FXML
    private void alternarReproduccion() {
        if (estaSonando()) {
            audio.pausar();
            refrescarReproductor();
            return;
        }
        if (!modoActivo.hayMas() && modoActivo.actual() == null) {
            mostrarAviso("No hay canciones para reproducir en el modo " + modoActivo.nombre() + ".");
            return;
        }
        if (modoActivo.actual() == null) {
            siguiente();
            return;
        }
        audio.reanudar();
        refrescarReproductor();
    }

    /** @return si hay audio sonando, segun la fuente activa */
    private boolean estaSonando() {
        return audio.reproduciendoProperty().get();
    }

    /** Abre la ventana que dibuja la estructura de datos del modo activo. */
    @FXML
    private void verEstructura() {
        if (ventanaEstructura != null && ventanaEstructura.isShowing()) {
            ventanaEstructura.toFront();
            return;
        }
        ventanaEstructura = new Stage();
        ventanaEstructura.initOwner(ventana());
        ventanaEstructura.setTitle("Estructura de datos");
        VentanaPixel.montar(ventanaEstructura, "ESTRUCTURA DE DATOS", visualizador.nodo());
        // Al cerrarla se suelta la referencia: si no, refrescar seguiria pintando en una ventana
        // que ya no existe.
        ventanaEstructura.setOnHidden(evento -> ventanaEstructura = null);

        // Se pinta ANTES de mostrar. Al reves —que es como estaba— show() dimensiona la ventana
        // con el lienzo todavia en su tamanio por defecto, y el arbol, que es mas alto, quedaba
        if (modoActivo != null) {
            visualizador.mostrar(modoActivo.estructuraVisual());
        }
        ventanaEstructura.show();
    }

    /** Abre la ventana con el resumen de lo mas escuchado. */
    @FXML
    private void verEstadisticas() {
        EstadisticasBiblioteca resumen = EstadisticasBiblioteca.de(biblioteca.todas());
        if (ventanaEstadisticas != null && ventanaEstadisticas.isShowing()) {
            ventanaEstadisticas.getScene().setRoot(
                    VentanaPixel.marco(ventanaEstadisticas, TITULO_ESTADISTICAS,
                            EstadisticasDialogo.construir(resumen)));
            ventanaEstadisticas.sizeToScene();
            ventanaEstadisticas.toFront();
            return;
        }
        ventanaEstadisticas = new Stage();
        ventanaEstadisticas.initOwner(ventana());
        ventanaEstadisticas.setTitle("Estadísticas");
        VentanaPixel.montar(ventanaEstadisticas, TITULO_ESTADISTICAS,
                EstadisticasDialogo.construir(resumen));
        ventanaEstadisticas.setOnHidden(evento -> ventanaEstadisticas = null);
        ventanaEstadisticas.show();
    }

    /** Repinta la estructura, si la ventana esta abierta. */
    private void refrescarVisualizador() {
        if (ventanaEstructura != null && ventanaEstructura.isShowing() && modoActivo != null) {
            visualizador.mostrar(modoActivo.estructuraVisual());
            ventanaEstructura.sizeToScene();
        }
    }

    /** Alterna entre usar cualquier fuente y quedarse solo con las que funcionan sin conexion. */
    @FXML
    private void alternarFuenteDeAudio() {
        boolean soloLocal = "AUTO".equals(etiquetaFuenteAudio.getText());
        audio.setEvitarRed(soloLocal);
        etiquetaFuenteAudio.setText(soloLocal ? "SOLO LOCAL" : "AUTO");
        marcarPestania(botonFuenteAudio, soloLocal);
        refrescarReproductor();
    }

    @FXML
    private void siguiente() {
        if (!modoActivo.hayMas()) {
            mostrarAviso("La cola está vacía. Usá \"RECARGAR COLA\" para volver a llenarla "
                    + "desde la biblioteca.");
            audio.detener();
            refrescarReproductor();
            return;
        }
        modoActivo.siguiente();
        iniciarPista();
    }

    @FXML
    private void anterior() {
        if (!modoActivo.permiteAnterior() || !modoActivo.hayMas()) {
            return;
        }
        modoActivo.anterior();
        iniciarPista();
    }

    /** Manda a sonar la cancion que el modo acaba de seleccionar. */
    private void iniciarPista() {
        limpiarAviso();
        Cancion pista = modoActivo.actual();
        audio.reproducir(pista);
        contarReproduccion(pista);
        refrescarReproductor();

        // El historial cambio de contenido, asi que hay que rearmar las filas; en cualquier otra
        // coleccion las filas son las mismas y basta con repintarlas para que la columna REPR.
        if (coleccionActiva instanceof ColeccionHistorial) {
            refrescarTabla();
        } else {
            tablaBiblioteca.refresh();
        }
    }

    /** Suma uno al contador de reproducciones de la cancion. */
    private void contarReproduccion(Cancion cancion) {
        if (cancion == null) {
            return;
        }
        biblioteca.editar(cancion,
                actual -> actual.setVecesReproducida(actual.getVecesReproducida() + 1));
    }

    /** @return duracion de la pista en segundos segun la fuente de audio */
    private int duracionDeLaPista() {
        long desdeLaFuente = audio.duracionMsProperty().get();
        if (desdeLaFuente > 0) {
            return (int) (desdeLaFuente / 1000);
        }
        // Un MP3 no publica su duracion hasta que carga la cabecera: mientras tanto se usa la
        // duracion declarada en la metadata para que la barra no aparezca vacia.
        Cancion actual = modoActivo == null ? null : modoActivo.actual();
        if (actual == null || actual.getDuracionSegundos() <= 0) {
            return DURACION_SIMULADA_POR_DEFECTO;
        }
        return actual.getDuracionSegundos();
    }

    // --- Biblioteca (CRUD) ---

    @FXML
    private void agregarCancion() {
        Optional<DatosCancion> datos = AgregarCancionController.mostrar(ventana(), null);
        datos.ifPresent(valores -> {
            Cancion nueva = valores.crearCancion();
            biblioteca.agregar(nueva);

            // Si se estaba viendo una lista, la cancion entra tambien en ella: es lo que espera
            // quien abre "Mi lista" y pulsa AGREGAR. En la biblioteca entera no hay nada que
            if (coleccionActiva instanceof ColeccionPlaylist mostrada) {
                listas.agregarCancion(mostrada.playlist(), nueva);
                activarModo(modoActivo);
                refrescarSelectorDeColecciones();
            }

            descargarPortadaEnSegundoPlano(nueva);
            seleccionarEnTabla(nueva);
            limpiarAviso();
        });
    }

    @FXML
    private void editarCancion() {
        Cancion seleccionada = tablaBiblioteca.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            return;
        }
        Optional<DatosCancion> datos = AgregarCancionController.mostrar(ventana(), seleccionada);
        datos.ifPresent(valores -> {
            // Se edita a traves de la biblioteca y no mutando la cancion a mano: es lo que abre la
            // ventana antes/despues que necesita el arbol del modo alfabetico para recolocarla.
            biblioteca.editar(seleccionada, valores::aplicarA);
            descargarPortadaEnSegundoPlano(seleccionada);
            seleccionarEnTabla(seleccionada);
            limpiarAviso();
        });
    }

    /** Baja la caratula de una cancion sin bloquear la interfaz. */
    private void descargarPortadaEnSegundoPlano(Cancion cancion) {
        if (cancion.getUrlPortadaRemota() == null || cancion.getUrlPortadaRemota().isBlank()) {
            return;
        }
        Task<Optional<Path>> tarea = new Task<>() {
            @Override
            protected Optional<Path> call() {
                return portadas.obtener(cancion.getId(), cancion.getUrlPortadaRemota());
            }
        };
        tarea.setOnSucceeded(evento -> tarea.getValue().ifPresent(ruta -> {
            biblioteca.editar(cancion, objetivo -> objetivo.setRutaPortada(ruta.toString()));
            cachePortadas.remove(cancion.getId());
            refrescarReproductor();
        }));

        Thread hilo = new Thread(tarea, "descarga-portada");
        hilo.setDaemon(true);
        hilo.start();
    }

    @FXML
    private void eliminarCancion() {
        Cancion seleccionada = tablaBiblioteca.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            return;
        }
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.initOwner(ventana());
        confirmacion.setTitle("Eliminar canción");
        confirmacion.setHeaderText(null);
        confirmacion.setContentText("¿Eliminar \"" + seleccionada.getTitulo()
                + "\" de la biblioteca?");
        aplicarEstilos(confirmacion);

        Optional<ButtonType> respuesta = confirmacion.showAndWait();
        if (respuesta.isPresent() && respuesta.get() == ButtonType.OK) {
            biblioteca.eliminar(seleccionada);
        }
    }

    /** Alterna entre el tema oscuro y el claro. */
    @FXML
    private void alternarTema() {
        var raiz = tablaBiblioteca.getScene().getRoot();
        boolean pasandoAClaro = !raiz.getStyleClass().contains(CLASE_TEMA_CLARO);
        if (pasandoAClaro) {
            raiz.getStyleClass().add(CLASE_TEMA_CLARO);
        } else {
            raiz.getStyleClass().remove(CLASE_TEMA_CLARO);
        }
        botonTema.setText(pasandoAClaro ? "MODO: CLARO" : "MODO: OSCURO");
        // El tema no es solo paleta: el modo oscuro lleva el traje negro y el claro el rojo.
        capaSpidey.usarTrajeNegro(!pasandoAClaro);

        // Las ventanas que se pueden quedar abiertas mientras se cambia el tema. Los diálogos
        // modales no hacen falta: mientras están abiertos no se puede pulsar el botón del tema, y
        VentanaPixel.aplicarTema(ventanaEstructura, pasandoAClaro);
        VentanaPixel.aplicarTema(ventanaEstadisticas, pasandoAClaro);
        // El visualizador pinta sobre un lienzo, donde el CSS no llega: hay que repintarlo para
        // que cambie de paleta.
        refrescarVisualizador();
    }

    // --- Barra de titulo propia ---

    /** Habilita arrastrar la ventana desde la barra de titulo. */
    private void prepararBarraTitulo() {
        barraTitulo.setOnMousePressed(evento -> {
            desplazamientoArrastreX = evento.getSceneX();
            desplazamientoArrastreY = evento.getSceneY();
        });
        barraTitulo.setOnMouseDragged(evento -> {
            Stage ventana = ventanaPrincipal();
            if (ventana == null || estaAgrandada()) {
                return;
            }
            ventana.setX(evento.getScreenX() - desplazamientoArrastreX);
            ventana.setY(evento.getScreenY() - desplazamientoArrastreY);
        });
        // Doble clic en la barra: el gesto habitual para maximizar y restaurar.
        barraTitulo.setOnMouseClicked(evento -> {
            if (evento.getClickCount() == 2) {
                alternarMaximizar();
            }
        });
    }

    @FXML
    private void minimizarVentana() {
        Stage ventana = ventanaPrincipal();
        if (ventana != null) {
            ventana.setIconified(true);
        }
    }

    /** Agranda la ventana a la pantalla util, o la devuelve a su tamanio anterior. */
    @FXML
    private void alternarMaximizar() {
        Stage ventana = ventanaPrincipal();
        if (ventana == null) {
            return;
        }
        if (tamanioAnterior != null) {
            ventana.setX(tamanioAnterior.getMinX());
            ventana.setY(tamanioAnterior.getMinY());
            ventana.setWidth(tamanioAnterior.getWidth());
            ventana.setHeight(tamanioAnterior.getHeight());
            tamanioAnterior = null;
            return;
        }
        tamanioAnterior = new Rectangle2D(
                ventana.getX(), ventana.getY(), ventana.getWidth(), ventana.getHeight());

        Rectangle2D util = Screen.getPrimary().getVisualBounds();
        ventana.setX(util.getMinX());
        ventana.setY(util.getMinY());
        ventana.setWidth(util.getWidth());
        ventana.setHeight(util.getHeight());
    }

    /** @return {@code true} si la ventana esta agrandada a la pantalla util */
    private boolean estaAgrandada() {
        return tamanioAnterior != null;
    }

    @FXML
    private void cerrarVentana() {
        alCerrar();
        Stage ventana = ventanaPrincipal();
        if (ventana != null) {
            ventana.close();
        }
    }

    private Stage ventanaPrincipal() {
        return (Stage) (tablaBiblioteca.getScene() == null
                ? null : tablaBiblioteca.getScene().getWindow());
    }

    @FXML
    private void buscar() {
        refrescarTabla();
    }

    /** Deja el buscador como estaba: sin texto y sin restringir a ningun campo. */
    @FXML
    private void limpiarBusqueda() {
        // Primero el value y luego el editor: al poner el value en null, JavaFX vacia el editor por
        // su cuenta, pero si el texto se escribio a mano el value ya era null y no se entera.
        campoBusqueda.setValue(null);
        campoBusqueda.getEditor().clear();
        selectorCampo.setValue(FiltroDeCampo.TODO.etiqueta());
    }

    // --- ObservadorBiblioteca: mantiene el modo activo en sincronia ---

    @Override
    public void cancionAgregada(Cancion cancion) {
        if (modoActivo != null) {
            modoActivo.agregar(cancion);
        }
        refrescarTabla();
        refrescarReproductor();
    }

    @Override
    public void cancionEliminada(Cancion cancion) {
        if (modoActivo != null) {
            modoActivo.eliminar(cancion);
        }
        // Se limpia tambien la caratula guardada, para no dejar basura en data/covers.
        cachePortadas.remove(cancion.getId());
        portadas.borrar(cancion.getId());
        refrescarTabla();
        refrescarReproductor();
    }

    @Override
    public void antesDeEditar(Cancion cancion) {
        if (modoActivo != null) {
            modoActivo.prepararEdicion(cancion);
        }
    }

    @Override
    public void despuesDeEditar(Cancion cancion) {
        if (modoActivo != null) {
            modoActivo.confirmarEdicion(cancion);
        }
        refrescarTabla();
        refrescarReproductor();
    }

    @Override
    public void bibliotecaRecargada(Iterable<Cancion> canciones) {
        if (modoActivo != null) {
            modoActivo.cargar(canciones);
        }
        refrescarTabla();
        refrescarReproductor();
    }

    // --- Refresco de la vista ---

    /** Repinta la tabla con la coleccion elegida, filtrada por lo que haya en el buscador. */
    private void refrescarTabla() {
        Cancion seleccionada = tablaBiblioteca.getSelectionModel().getSelectedItem();

        List<Cancion> visibles = coleccionActiva.canciones();
        String filtro = campoBusqueda.getEditor().getText();
        if (filtro != null && !filtro.isBlank()) {
            // Se filtra sobre la coleccion visible, no sobre la biblioteca entera: si se esta
            // viendo una lista, buscar dentro de ella no debe sacar canciones de fuera.
            FiltroDeCampo campo = campoDeFiltro();
            visibles = visibles.stream().filter(cancion -> campo.coincide(cancion, filtro)).toList();
        }

        filasBiblioteca.setAll(visibles);
        etiquetaContador.setText(visibles.size() + " CANCIONES");
        explicarTablaVacia();
        if (seleccionada != null && filasBiblioteca.contains(seleccionada)) {
            tablaBiblioteca.getSelectionModel().select(seleccionada);
        }
        actualizarBotonesDeSeleccion();
    }

    /** Explica que hacer cuando la tabla queda vacia. */
    private void explicarTablaVacia() {
        String mensaje;
        if (coleccionActiva instanceof ColeccionPlaylist) {
            mensaje = "ESTA LISTA ESTÁ VACÍA\n\n"
                    + "1. Elegí TODA LA BIBLIOTECA en el selector de arriba\n"
                    + "2. Clic derecho sobre una canción\n"
                    + "3. \"Agregar a " + coleccionActiva.nombre() + "\"";
        } else if (coleccionActiva instanceof ColeccionHistorial) {
            mensaje = "TODAVÍA NO HAS REPRODUCIDO NADA\n\n"
                    + "Las canciones aparecen acá al reproducirlas,\n"
                    + "de la más reciente a la más antigua.";
        } else if (coleccionActiva instanceof ColeccionFavoritas) {
            mensaje = "NO HAY FAVORITAS TODAVÍA\n\n"
                    + "Elegí TODA LA BIBLIOTECA y marcá canciones con\n"
                    + "clic derecho → Agregar a ★ FAVORITAS";
        } else {
            mensaje = "NO HAY CANCIONES\n\nUsá el botón AGREGAR";
        }

        Label explicacion = new Label(mensaje);
        explicacion.getStyleClass().add("texto-tenue");
        explicacion.setWrapText(true);
        explicacion.setTextAlignment(TextAlignment.CENTER);
        tablaBiblioteca.setPlaceholder(explicacion);
    }

    private void refrescarReproductor() {
        Cancion actual = modoActivo.actual();

        if (actual == null) {
            etiquetaTitulo.setText("SIN REPRODUCCIÓN");
            etiquetaArtista.setText("-");
            etiquetaDetalle.setText("MODO " + modoActivo.nombre().toUpperCase()
                    + "\n" + modoActivo.estructuraUsada().toUpperCase());
        } else {
            etiquetaTitulo.setText(actual.getTitulo().toUpperCase());
            etiquetaArtista.setText(actual.getArtista().toUpperCase());
            etiquetaDetalle.setText(detalleDe(actual));
        }

        imagenPortada.setImage(portadaDe(actual));
        filasSiguientes.setAll(modoActivo.listaReproduccion());
        actualizarBotonesDeTransporte();
        actualizarBotonAccionModo();
        actualizarMarquesina(actual);
        refrescarProgreso();
        // La estructura cambia con cada canción: el cursor se mueve, la cola se vacía, el árbol
        // resalta otro nodo. Si la ventana está abierta, tiene que reflejarlo.
        refrescarVisualizador();
    }

    /** Pinta la barra segmentada segun el avance real de la reproduccion. */
    private void refrescarProgreso() {
        int total = duracionDeLaPista();
        boolean hayPista = modoActivo != null && modoActivo.actual() != null;
        int segundos = arrastrandoProgreso
                ? (int) Math.round(avanceArrastrado * total)
                : (int) (audio.posicionMsProperty().get() / 1000);
        double avance = (!hayPista || total <= 0)
                ? 0
                : Math.min(1.0, (double) segundos / total);
        int encendidos = (int) Math.round(avance * BLOQUES_PROGRESO);

        // El cursor es el ultimo bloque encendido. Sin el, con 28 bloques rojos todos iguales
        // habia que contarlos para saber por donde iba la cancion.
        int cursor = hayPista ? Math.min(encendidos, BLOQUES_PROGRESO) - 1 : -1;

        for (int i = 0; i < bloquesProgreso.size(); i++) {
            Rectangle bloque = bloquesProgreso.get(i);
            bloque.getStyleClass().removeAll("bloque-progreso-encendido", CLASE_CURSOR_PROGRESO);
            if (i < encendidos) {
                bloque.getStyleClass().add("bloque-progreso-encendido");
            }
            boolean esCursor = i == cursor;
            if (esCursor) {
                // "bloque-progreso" ya lo lleva desde que se creo; volver a anadirla en cada
                // refresco haria crecer la lista de clases sin parar.
                bloque.getStyleClass().add(CLASE_CURSOR_PROGRESO);
            }
            // Sobresale por arriba y por abajo: el color solo no basta para localizarlo de un
            // vistazo en una fila de bloques del mismo tamanio.
            bloque.setWidth(esCursor ? ANCHO_BLOQUE + 4 : ANCHO_BLOQUE);
            bloque.setHeight(esCursor ? ALTO_BLOQUE + 8 : ALTO_BLOQUE);
        }
        etiquetaTiempoActual.setText(hayPista ? formatearSegundos(segundos) : "0:00");
        etiquetaTiempoTotal.setText(hayPista ? formatearSegundos(total) : "0:00");
    }

    private void actualizarBotonesDeTransporte() {
        boolean sonando = estaSonando();

        // El boton "Anterior" se habilita preguntandole al modo, nunca mirando de que clase es.
        botonAnterior.setDisable(!modoActivo.permiteAnterior());
        botonSiguiente.setDisable(!modoActivo.hayMas());
        botonReproducir.setDisable(
                !sonando && !modoActivo.hayMas() && modoActivo.actual() == null);

        // El icono anuncia la accion disponible, no el estado actual.
        iconoReproducir.setImage(sonando ? iconoPausa : iconoPlay);
        barrasSonido.sincronizar(sonando);
        // Si la fuente dejo de mandar espectro —al pasar a Spotify, que no puede analizarlo—
        // las barras volverian a la animacion decorativa en vez de quedarse congeladas.
        barrasSonido.revisarSiSiguenLlegandoDatos();
    }

    private void actualizarBotonAccionModo() {
        if (modoActivo == modoAleatorio) {
            botonAccionModo.setText("VOLVER A MEZCLAR");
            botonAccionModo.setDisable(biblioteca.tamanio() < 2);
        } else if (modoActivo == modoOrdenLlegada) {
            botonAccionModo.setText("RECARGAR COLA");
            botonAccionModo.setDisable(biblioteca.estaVacia());
        } else {
            botonAccionModo.setText("VOLVER AL INICIO");
            botonAccionModo.setDisable(biblioteca.estaVacia());
        }
    }

    private void actualizarBotonesDeSeleccion() {
        boolean haySeleccion = tablaBiblioteca.getSelectionModel().getSelectedItem() != null;
        botonEditar.setDisable(!haySeleccion);
        botonEliminar.setDisable(!haySeleccion);
    }

    private void actualizarMarquesina(Cancion actual) {
        String texto = (actual == null)
                ? "REPRODUCTOR CAMELLOS VS ENANOS  •  MODO " + modoActivo.nombre().toUpperCase()
                        + "  •  " + modoActivo.estructuraUsada().toUpperCase()
                        + "  •  " + biblioteca.tamanio() + " CANCIONES EN BIBLIOTECA  •  "
                : "AHORA SUENA  •  " + actual.getTitulo().toUpperCase()
                        + "  •  " + actual.getArtista().toUpperCase()
                        + "  •  CALIFICACIÓN " + actual.getCalificacion() + "/100"
                        + "  •  MODO " + modoActivo.nombre().toUpperCase() + "  •  ";
        etiquetaMarquesina.setText(texto);
    }

    // --- Utilidades ---

    /** Devuelve la caratula que debe mostrarse para una cancion. */
    private Image portadaDe(Cancion cancion) {
        if (cancion == null) {
            return portadaPorDefecto;
        }
        Image enMemoria = cachePortadas.get(cancion.getId());
        if (enMemoria != null) {
            return enMemoria;
        }

        String ruta = cancion.getRutaPortada();
        if (ruta != null && !ruta.isBlank()) {
            File archivo = new File(ruta);
            if (archivo.isFile()) {
                Image imagen = new Image(archivo.toURI().toString());
                if (!imagen.isError()) {
                    cachePortadas.put(cancion.getId(), imagen);
                    return imagen;
                }
            }
        }
        return portadaPorDefecto;
    }

    /** Arma el texto de detalle de la cancion en curso. */
    private String detalleDe(Cancion cancion) {
        StringBuilder detalle = new StringBuilder(cancion.getAlbum().toUpperCase());
        if (cancion.getAnio() > 0) {
            detalle.append("  •  ").append(cancion.getAnio());
        }
        detalle.append("\n").append(cancion.getGenero().toUpperCase())
                .append("  •  ").append(cancion.getCalificacion()).append("/100");
        if (cancion.isFavorita()) {
            detalle.append("  •  FAVORITA");
        }
        detalle.append("\nAUDIO: ").append(audio.nombreFuente().toUpperCase());
        return detalle.toString();
    }

    private static String textoAnio(Cancion cancion) {
        return cancion.getAnio() > 0 ? String.valueOf(cancion.getAnio()) : "-";
    }

    private static String textoCalificacion(Cancion cancion) {
        return String.valueOf(cancion.getCalificacion());
    }

    private static String formatearSegundos(int segundos) {
        return String.format("%d:%02d", segundos / 60, segundos % 60);
    }

    private void seleccionarEnTabla(Cancion cancion) {
        refrescarTabla();
        tablaBiblioteca.getSelectionModel().select(cancion);
        tablaBiblioteca.scrollTo(cancion);
    }

    private void mostrarAviso(String mensaje) {
        etiquetaAviso.setText("• " + mensaje);
        etiquetaAviso.setVisible(true);
        etiquetaAviso.setManaged(true);
    }

    private void limpiarAviso() {
        etiquetaAviso.setVisible(false);
        etiquetaAviso.setManaged(false);
    }

    private javafx.stage.Window ventana() {
        return tablaBiblioteca.getScene() == null ? null : tablaBiblioteca.getScene().getWindow();
    }

    /** Le pega la hoja de estilos pixel a los dialogos del sistema. */
    private void aplicarEstilos(Alert alerta) {
        URL hoja = getClass().getResource("/vista/estilos.css");
        if (hoja != null) {
            alerta.getDialogPane().getStylesheets().add(hoja.toExternalForm());
        }
    }

    /** Guarda la biblioteca antes de cerrar la aplicacion. */
    public void alCerrar() {
        // Detener el audio es obligatorio, no cortesia: un MediaPlayer vivo retiene un hilo nativo
        // que puede dejar el proceso colgado despues de cerrar la ventana.
        audio.detener();
        if (animacionMarquesina != null) {
            animacionMarquesina.stop();
        }
        capaSpidey.detener();
        barrasSonido.detener();
        biblioteca.guardar();
    }
}
