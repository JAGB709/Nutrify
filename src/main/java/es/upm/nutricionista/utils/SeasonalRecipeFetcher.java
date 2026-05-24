package es.upm.nutricionista.utils;

import es.upm.nutricionista.modelo.Macronutrients;
import es.upm.nutricionista.modelo.Recipe;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * Obtiene recetas de temporada del mes actual desde TheMealDB (API pública, sin autenticación).
 *
 * La temporada se determina por el mes actual y se traduce a ingredientes en inglés
 * para la API de TheMealDB. El resultado varía cada mes, lo que justifica la
 * percepción ambiental dinámica de AgentePercepcion.
 *
 * API usada: https://www.themealdb.com/api/json/v1/1/
 *   - filter.php?i={ingrediente} → lista de recetas que usan ese ingrediente
 *   - lookup.php?i={idMeal}      → detalle completo de la receta
 */
public class SeasonalRecipeFetcher {

    private static final String BASE_URL    = "https://www.themealdb.com/api/json/v1/1/";
    private static final int    CONNECT_MS  = 5_000;
    private static final int    READ_MS     = 8_000;
    // IDs base para recetas de temporada (evita colisión con el dataset local)
    private static final int    TEMPORAL_ID_BASE = 90_000;

    // Ingredientes de temporada por mes (en inglés para TheMealDB)
    private static final Map<Integer, String[]> SEASONAL;
    static {
        Map<Integer, String[]> m = new HashMap<>();
        m.put(1,  new String[]{"Potato", "Carrot", "Spinach"});
        m.put(2,  new String[]{"Cabbage", "Orange", "Onion"});
        m.put(3,  new String[]{"Asparagus", "Peas", "Leek"});
        m.put(4,  new String[]{"Strawberries", "Lettuce", "Artichoke"});
        m.put(5,  new String[]{"Tomato", "Garlic", "Broad Beans"});
        m.put(6,  new String[]{"Courgette", "Tomato", "Pepper"});
        m.put(7,  new String[]{"Aubergine", "Pepper", "Watermelon"});
        m.put(8,  new String[]{"Aubergine", "Peach", "Green Beans"});
        m.put(9,  new String[]{"Mushroom", "Pumpkin", "Grapes"});
        m.put(10, new String[]{"Pumpkin", "Apple", "Mushroom"});
        m.put(11, new String[]{"Cauliflower", "Leek", "Broccoli"});
        m.put(12, new String[]{"Broccoli", "Potato", "Onion"});
        SEASONAL = Collections.unmodifiableMap(m);
    }

    /**
     * Descarga hasta {@code maxResults} recetas de temporada basadas en el mes actual.
     * Si la red no está disponible, devuelve lista vacía sin lanzar excepción.
     *
     * @param maxResults número máximo de recetas a devolver
     * @return lista de recetas de temporada (puede estar vacía si falla la red)
     */
    public static List<Recipe> fetch(int maxResults) {
        int month = LocalDate.now().getMonthValue();
        String[] ingredients = SEASONAL.getOrDefault(month, new String[]{"Tomato"});
        System.out.println("[SeasonalRecipeFetcher] Mes " + month
                + " → ingredientes de temporada: " + Arrays.toString(ingredients));

        List<Recipe> out = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (String ingredient : ingredients) {
            if (out.size() >= maxResults) break;
            try {
                List<String> mealIds = getMealIdsByIngredient(ingredient);
                for (String id : mealIds) {
                    if (out.size() >= maxResults) break;
                    if (seenIds.contains(id)) continue;
                    seenIds.add(id);
                    Recipe r = getMealById(id, out.size());
                    if (r != null) {
                        out.add(r);
                        System.out.println("[SeasonalRecipeFetcher] Receta de temporada: "
                                + r.getNombre() + " (ingrediente: " + ingredient + ")");
                    }
                }
            } catch (IOException e) {
                System.err.println("[SeasonalRecipeFetcher] Error con ingrediente '"
                        + ingredient + "': " + e.getMessage());
            }
        }

        System.out.println("[SeasonalRecipeFetcher] " + out.size()
                + " receta(s) de temporada obtenida(s).");
        return out;
    }

    //  Llamadas a la API

    private static List<String> getMealIdsByIngredient(String ingredient) throws IOException {
        String url = BASE_URL + "filter.php?i=" + ingredient.replace(" ", "_");
        String json = httpGet(url);
        return parseIds(json);
    }

    private static Recipe getMealById(String mealId, int indexForId) throws IOException {
        String url = BASE_URL + "lookup.php?i=" + mealId;
        String json = httpGet(url);
        return parseMeal(json, indexForId);
    }

    //  HTTP 

    private static String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_MS);
        conn.setReadTimeout(READ_MS);
        conn.setRequestProperty("Accept", "application/json");
        int status = conn.getResponseCode();
        if (status != 200) throw new IOException("HTTP " + status + " en " + urlStr);
        try (BufferedReader rd = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    //  Parsers 

    /**
     * Extrae los "idMeal" del JSON de filter.php.
     * Formato de respuesta: {"meals":[{"strMeal":"...","idMeal":"52772",...},...]}.
     */
    private static List<String> parseIds(String json) {
        List<String> ids = new ArrayList<>();
        // Detectar respuesta nula: {"meals":null}
        if (json.contains("\"meals\":null")) return ids;
        int pos = 0;
        while (true) {
            int k = json.indexOf("\"idMeal\"", pos);
            if (k < 0) break;
            int colon = json.indexOf(':', k + 8);
            if (colon < 0) break;
            int start = colon + 1;
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"'))
                start++;
            int end = start;
            while (end < json.length() && json.charAt(end) != '"'
                    && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            String id = json.substring(start, end).trim();
            if (!id.isEmpty()) ids.add(id);
            pos = end + 1;
        }
        return ids;
    }

    /**
     * Extrae una Recipe del JSON de lookup.php.
     * Formato de respuesta: {"meals":[{objeto con todos los campos}]}.
     */
    private static Recipe parseMeal(String json, int indexForId) {
        if (json.contains("\"meals\":null")) return null;
        int mealsIdx = json.indexOf("\"meals\":");
        if (mealsIdx < 0) return null;
        int arrStart = json.indexOf('[', mealsIdx);
        if (arrStart < 0) return null;
        int objStart = json.indexOf('{', arrStart);
        if (objStart < 0) return null;
        int objEnd = matchingBrace(json, objStart);
        if (objEnd < 0) return null;
        String meal = json.substring(objStart, objEnd + 1);

        String nombre       = parseStr(meal, "strMeal");
        String instructions = parseStr(meal, "strInstructions");
        if (nombre == null || nombre.trim().isEmpty()) return null;

        // Ingredientes: strIngredient1..20 combinados con strMeasure1..20
        List<String> ings = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            String ing = parseStr(meal, "strIngredient" + i);
            if (ing == null || ing.trim().isEmpty()) break;
            String mea = parseStr(meal, "strMeasure" + i);
            String entry = (mea != null && !mea.trim().isEmpty())
                    ? mea.trim() + " " + ing.trim()
                    : ing.trim();
            ings.add(entry);
        }

        List<String> pasos = splitInstructions(instructions);
        int id = TEMPORAL_ID_BASE + indexForId;
        return new Recipe(id, nombre.trim(), ings, pasos, new Macronutrients(0, 0, 0, 0));
    }

    /**
     * Extrae el valor de una cadena JSON por nombre de campo.
     * Maneja secuencias de escape (\n, \r, \", \\, \t).
     */
    private static String parseStr(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return null;
        int colon = json.indexOf(':', k + key.length() + 2);
        if (colon < 0) return null;
        int s = colon + 1;
        while (s < json.length() && json.charAt(s) == ' ') s++;
        if (s >= json.length()) return null;
        if (json.startsWith("null", s)) return null;
        if (json.charAt(s) != '"') return null;

        StringBuilder sb = new StringBuilder();
        for (int i = s + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char nx = json.charAt(i + 1);
                switch (nx) {
                    case 'n':  sb.append('\n'); i++; break;
                    case 'r':  i++; break; // omitir \r
                    case '"':  sb.append('"'); i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    case 't':  sb.append('\t'); i++; break;
                    default:   sb.append(nx); i++; break;
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int matchingBrace(String s, int start) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { if (--depth == 0) return i; }
        }
        return -1;
    }

    /**
     * Divide las instrucciones de TheMealDB en pasos individuales.
     * Prefiere párrafos (doble salto de línea); en su defecto, línea a línea.
     */
    private static List<String> splitInstructions(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        raw = raw.trim();

        // Intentar split por doble salto de línea (párrafos)
        String[] chunks = raw.split("\\r?\\n\\r?\\n+");
        if (chunks.length > 1) {
            for (String chunk : chunks) {
                String c = chunk.trim().replaceAll("[ \\t]+", " ");
                if (!c.isEmpty()) out.add(c);
            }
            return out;
        }

        // Split por línea simple, quitando prefijos de numeración
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            String c = line.trim().replaceAll("^\\d+[.):]?\\s*", "");
            if (!c.isEmpty()) out.add(c);
        }

        // Si quedó como un único bloque grande, devolver tal cual
        if (out.isEmpty()) out.add(raw);
        return out;
    }
}
