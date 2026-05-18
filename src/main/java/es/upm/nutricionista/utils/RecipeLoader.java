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
 * Loads recipes from recetas.json (classpath resource) into a List&lt;Recipe&gt;.
 * Uses only standard Java I/O — no external JSON library required.
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
