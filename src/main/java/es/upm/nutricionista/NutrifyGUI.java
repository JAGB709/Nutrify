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
 */
public class NutrifyGUI extends JFrame {

    private final JTextField    campoIngredientes;
    private final JButton       botonBuscar;
    private final JTextArea     areaResultados;
    private       BusquedaListener listener;

    public interface BusquedaListener {
        void onBuscar(String ingredientes);
    }

    public NutrifyGUI() {
        super("Nutrify — Nutricionista Virtual");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(780, 580);
        setLocationRelativeTo(null);

        // ── Panel principal ────────────────────────────────────────────────
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Título
        JLabel titulo = new JLabel("Nutrify — Nutricionista Virtual", SwingConstants.CENTER);
        titulo.setFont(new Font("SansSerif", Font.BOLD, 20));
        main.add(titulo, BorderLayout.NORTH);

        // ── Panel de entrada ───────────────────────────────────────────────
        JPanel panelEntrada = new JPanel(new BorderLayout(6, 0));
        panelEntrada.add(new JLabel("Ingredientes: "), BorderLayout.WEST);

        campoIngredientes = new JTextField();
        campoIngredientes.setToolTipText("Escribe los ingredientes separados por coma");
        panelEntrada.add(campoIngredientes, BorderLayout.CENTER);

        botonBuscar = new JButton("Buscar Recetas");
        botonBuscar.setFont(new Font("SansSerif", Font.BOLD, 13));
        panelEntrada.add(botonBuscar, BorderLayout.EAST);

        // ── Área de resultados ─────────────────────────────────────────────
        areaResultados = new JTextArea();
        areaResultados.setEditable(false);
        areaResultados.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        areaResultados.setLineWrap(true);
        areaResultados.setWrapStyleWord(true);
        areaResultados.setText("\n  Introduce ingredientes y pulsa «Buscar Recetas».\n"
                + "  Ejemplo: tomate, huevo, cebolla\n");

        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.add(panelEntrada, BorderLayout.NORTH);
        centro.add(new JScrollPane(areaResultados), BorderLayout.CENTER);
        main.add(centro, BorderLayout.CENTER);

        add(main);

        // ── Acciones ───────────────────────────────────────────────────────
        botonBuscar.addActionListener((ActionEvent e) -> lanzarBusqueda());
        campoIngredientes.addActionListener((ActionEvent e) -> lanzarBusqueda());
    }

    private void lanzarBusqueda() {
        String input = campoIngredientes.getText().trim();
        if (input.isEmpty()) return;
        if (listener == null) {
            mostrarResultados("\n  [Error] Sistema no inicializado todavía.\n");
            return;
        }
        botonBuscar.setEnabled(false);
        areaResultados.setText("\n  Buscando recetas para: " + input + " ...\n");
        listener.onBuscar(input);
    }

    /**
     * Actualiza el área de resultados. Debe llamarse desde el hilo de eventos Swing
     * o con SwingUtilities.invokeLater().
     */
    public void mostrarResultados(String texto) {
        SwingUtilities.invokeLater(() -> {
            areaResultados.setText(texto);
            areaResultados.setCaretPosition(0);
            botonBuscar.setEnabled(true);
        });
    }

    public void setListener(BusquedaListener listener) {
        this.listener = listener;
    }
}
