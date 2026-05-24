package es.upm.nutricionista.modelo;

/**
 * Representa los macronutrientes de una receta: calorías, proteínas, grasas y carbohidratos.
 * Se utiliza para mostrar esta información en la interfaz de usuario y para filtrar recetas según necesidades nutricionales.
 */

public class Macronutrients {
    private final int calorias;
    private final int proteinas;
    private final int grasas;
    private final int carbohidratos;

    public Macronutrients(int calorias, int proteinas, int grasas, int carbohidratos) {
        this.calorias = calorias;
        this.proteinas = proteinas;
        this.grasas = grasas;
        this.carbohidratos = carbohidratos;
    }

    public int getCalorias()      { return calorias; }
    public int getProteinas()     { return proteinas; }
    public int getGrasas()        { return grasas; }
    public int getCarbohidratos() { return carbohidratos; }
}
