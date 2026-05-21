package es.upm.nutricionista;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * NutrifyGUI — interfaz gráfica Swing del sistema Nutrify.
 *
 * El usuario escribe ingredientes en el campo de texto y pulsa "Buscar Recetas"
 * (o Enter). La búsqueda se delega al AgentePercepcion via el listener registrado.
 * Los resultados llegan a través de mostrarResultados(), llamado desde AgenteInterfaz.
 * El área de resultados usa JEditorPane con content-type "text/html" para
 * renderizar tarjetas con código de colores, scores y tabla de macronutrientes.
 */
public class NutrifyGUI extends JFrame {

    private final JTextField  campoIngredientes;
    private final JButton     botonBuscar;
    private final JEditorPane areaResultados;
    private       BusquedaListener listener;

    public interface BusquedaListener {
        void onBuscar(String ingredientes);
    }

    public NutrifyGUI() {
        super("Nutrify — Nutricionista Virtual");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(780, 580);
        setLocationRelativeTo(null);

        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel titulo = new JLabel("Nutrify — Nutricionista Virtual", SwingConstants.CENTER);
        titulo.setFont(new Font("SansSerif", Font.BOLD, 20));
        main.add(titulo, BorderLayout.NORTH);

        JPanel panelEntrada = new JPanel(new BorderLayout(6, 0));
        panelEntrada.add(new JLabel("Ingredientes: "), BorderLayout.WEST);

        campoIngredientes = new JTextField();
        campoIngredientes.setToolTipText("Escribe los ingredientes separados por coma");
        panelEntrada.add(campoIngredientes, BorderLayout.CENTER);

        botonBuscar = new JButton("Buscar Recetas");
        botonBuscar.setFont(new Font("SansSerif", Font.BOLD, 13));
        panelEntrada.add(botonBuscar, BorderLayout.EAST);

        areaResultados = new JEditorPane();
        areaResultados.setEditable(false);
        areaResultados.setContentType("text/html");
        areaResultados.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        areaResultados.setFont(new Font("Arial", Font.PLAIN, 13));
        areaResultados.setText(
            "<html><body bgcolor='#f8f8f8' style='font-family:Arial,sans-serif;"
            + "font-size:13px;margin:15px;'>"
            + "<p>Introduce ingredientes y pulsa <b>&#171;Buscar Recetas&#187;</b>.</p>"
            + "<p>Ejemplo: <i>tomate, huevo, cebolla</i></p>"
            + "</body></html>");

        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.add(panelEntrada, BorderLayout.NORTH);
        centro.add(new JScrollPane(areaResultados), BorderLayout.CENTER);
        main.add(centro, BorderLayout.CENTER);

        add(main);

        botonBuscar.addActionListener((ActionEvent e) -> lanzarBusqueda());
        campoIngredientes.addActionListener((ActionEvent e) -> lanzarBusqueda());
    }

    private void lanzarBusqueda() {
        String input = campoIngredientes.getText().trim();
        if (input.isEmpty()) return;
        if (listener == null) {
            mostrarResultados("<html><body><p><font color='red'>"
                    + "[Error] Sistema no inicializado todavía.</font></p></body></html>");
            return;
        }
        botonBuscar.setEnabled(false);
        areaResultados.setText(
            "<html><body bgcolor='#f8f8f8' style='font-family:Arial,sans-serif;"
            + "font-size:13px;margin:15px;'>"
            + "<p>Buscando recetas para: <b>" + input + "</b> ...</p>"
            + "</body></html>");
        listener.onBuscar(input);
    }

    /**
     * Actualiza el área de resultados con contenido HTML.
     * Debe llamarse desde el hilo de eventos Swing o con SwingUtilities.invokeLater().
     */
    public void mostrarResultados(String htmlTexto) {
        SwingUtilities.invokeLater(() -> {
            areaResultados.setText(htmlTexto);
            areaResultados.setCaretPosition(0);
            botonBuscar.setEnabled(true);
        });
    }

    public void setListener(BusquedaListener listener) {
        this.listener = listener;
    }
}
