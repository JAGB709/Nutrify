package es.upm.nutricionista.modelo;

import java.util.List;

public class Recipe {
    private final int id;
    private final String nombre;
    private final List<String> ingredientes;
    private final List<String> pasos;
    private final Macronutrients macronutrientes;

    public Recipe(int id, String nombre, List<String> ingredientes,
                  List<String> pasos, Macronutrients macronutrientes) {
        this.id = id;
        this.nombre = nombre;
        this.ingredientes = ingredientes;
        this.pasos = pasos;
        this.macronutrientes = macronutrientes;
    }

    public int getId()                          { return id; }
    public String getNombre()                   { return nombre; }
    public List<String> getIngredientes()       { return ingredientes; }
    public List<String> getPasos()              { return pasos; }
    public Macronutrients getMacronutrientes()  { return macronutrientes; }
}
