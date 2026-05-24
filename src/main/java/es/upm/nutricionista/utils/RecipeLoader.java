package es.upm.nutricionista.utils;

import es.upm.nutricionista.modelo.Macronutrients;
import es.upm.nutricionista.modelo.Recipe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilidad para cargar recetas desde un archivo JSON ubicado en el classpath. Proporciona métodos para leer el archivo, parsear su contenido y convertirlo en objetos Recipe.
 * El formato esperado del JSON es una lista de objetos con campos id, nombre, ingredientes (lista de strings), pasos (lista de strings) y macronutrientes (objeto con calorias, proteínas, grasas y carbohidratos).
 * Este loader es utilizado por el agente de búsqueda de recetas para inicializar su base de datos de recetas 
 * disponibles. Se implementa un parser manual para evitar dependencias externas, aunque en un proyecto real se recomendaría usar una biblioteca como Jackson o Gson para manejar JSON de manera más robusta y eficiente.
 */
public class RecipeLoader {

    private static final String RESOURCE_PATH = "recetas.json";

    public static List<Recipe> load() throws IOException {
        InputStream is = RecipeLoader.class.getClassLoader().getResourceAsStream(RESOURCE_PATH);
        if (is == null) {
            throw new IOException("Recurso no encontrado en classpath: " + RESOURCE_PATH);
        }
        return load(is);
    }
    // Método privado para cargar recetas desde un InputStream, utilizado por el método público que carga desde el classpath.
    public static List<Recipe> load(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line.trim());
            }
        }
        return parseRecipes(sb.toString());
    }

    // Método privado para parsear el contenido JSON y convertirlo en una lista de objetos Recipe. Implementa un parser manual básico.
    private static List<Recipe> parseRecipes(String json) {
        List<Recipe> recipes = new ArrayList<>();
        String content = json.trim();
        if (content.startsWith("[")) content = content.substring(1);
        if (content.endsWith("]")) content = content.substring(0, content.length() - 1);

        for (String obj : splitObjects(content)) {
            Recipe recipe = parseRecipe(obj.trim());
            if (recipe != null) recipes.add(recipe);
        }
        return recipes;
    }

    // Método auxiliar para dividir el contenido JSON en objetos individuales, manejando correctamente las estructuras anidadas y las cadenas con comillas.
    private static List<String> splitObjects(String content) {
        List<String> objects = new ArrayList<>();
        int depth = 0, start = -1;
        boolean inString = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\\' && inString) { i++; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') { if (depth == 0) start = i; depth++; }
            else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) { objects.add(content.substring(start, i + 1)); start = -1; }
            }
        }
        return objects;
    }

    // Método auxiliar para parsear un objeto JSON individual y convertirlo en un objeto Recipe. Extrae los campos necesarios y maneja posibles errores de formato.
    private static Recipe parseRecipe(String obj) {
        try {
            int id = Integer.parseInt(extractValue(obj, "id"));
            String nombre = extractValue(obj, "nombre");
            List<String> ingredientes = extractArray(obj, "ingredientes");
            List<String> pasos = extractArray(obj, "pasos");
            Macronutrients macros = extractMacros(obj);
            return new Recipe(id, nombre, ingredientes, pasos, macros);
        } catch (Exception e) {
            return null;
        }
    }

    // Métodos auxiliares para extraer valores de campos específicos del JSON, manejar arrays y objetos anidados, y convertirlos en los tipos adecuados para construir el objeto Recipe.

    private static String extractValue(String obj, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = obj.indexOf(search);
        if (keyIdx == -1) return "";
        int colonIdx = obj.indexOf(':', keyIdx);
        if (colonIdx == -1) return "";
        int start = colonIdx + 1;
        while (start < obj.length() && obj.charAt(start) == ' ') start++;
        if (start >= obj.length()) return "";
        if (obj.charAt(start) == '"') {
            int end = obj.indexOf('"', start + 1);
            return end == -1 ? "" : obj.substring(start + 1, end);
        } else {
            int end = start;
            while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-')) end++;
            return obj.substring(start, end).trim();
        }
    }

    // Método auxiliar para extraer un array de strings de un campo específico del JSON, manejando correctamente las comillas y los delimitadores.
    private static List<String> extractArray(String obj, String key) {
        List<String> items = new ArrayList<>();
        int keyIdx = obj.indexOf("\"" + key + "\"");
        if (keyIdx == -1) return items;
        int openBracket = obj.indexOf('[', keyIdx);
        if (openBracket == -1) return items;
        int closeBracket = -1, depth = 1;
        for (int i = openBracket + 1; i < obj.length(); i++) {
            char c = obj.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') { if (--depth == 0) { closeBracket = i; break; } }
        }
        if (closeBracket == -1) return items;
        String array = obj.substring(openBracket + 1, closeBracket).trim();
        if (array.isEmpty()) return items;
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            char c = array.charAt(i);
            if (c == '\\' && inQuotes && i + 1 < array.length()) { current.append(c); current.append(array.charAt(++i)); }
            else if (c == '"') { inQuotes = !inQuotes; }
            else if (c == ',' && !inQuotes) {
                String item = current.toString().trim();
                if (!item.isEmpty()) items.add(item);
                current = new StringBuilder();
            } else { current.append(c); }
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) items.add(last);
        return items;
    }


    // Método auxiliar para extraer los macronutrientes de un objeto JSON anidado dentro del campo "macronutrientes", y convertirlo en un objeto Macronutrients.
    private static Macronutrients extractMacros(String obj) {
        int keyIdx = obj.indexOf("\"macronutrientes\"");
        if (keyIdx == -1) return new Macronutrients(0, 0, 0, 0);
        int openBrace = obj.indexOf('{', keyIdx);
        int closeBrace = obj.indexOf('}', openBrace);
        String macroObj = obj.substring(openBrace, closeBrace + 1);
        try {
            int cal  = Integer.parseInt(extractValue(macroObj, "calorias"));
            int prot = Integer.parseInt(extractValue(macroObj, "proteinas"));
            int gras = Integer.parseInt(extractValue(macroObj, "grasas"));
            int carb = Integer.parseInt(extractValue(macroObj, "carbohidratos"));
            return new Macronutrients(cal, prot, gras, carb);
        } catch (NumberFormatException e) {
            return new Macronutrients(0, 0, 0, 0);
        }
    }
}
