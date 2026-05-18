package es.upm.nutricionista.ir;

public class TfIdfCalculator {

    /**
     * Log-frequency TF weight: 1 + log10(rawTf) if rawTf > 0, else 0.
     */
    public static double tfWeight(int rawTf) {
        return rawTf > 0 ? 1.0 + Math.log10(rawTf) : 0.0;
    }

    /**
     * IDF weight: log10(totalDocs / docFreq). Returns 0 if docFreq <= 0.
     */
    public static double idf(int totalDocs, int docFreq) {
        return docFreq > 0 ? Math.log10((double) totalDocs / docFreq) : 0.0;
    }

    /**
     * Combined TF-IDF: tfWeight(rawTf) * idf(totalDocs, docFreq).
     */
    public static double tfidf(int rawTf, int totalDocs, int docFreq) {
        return tfWeight(rawTf) * idf(totalDocs, docFreq);
    }
}
