package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds the bounded reference text handed to the Prompt renderer as the {@code context}
 * variable.
 *
 * <p>Only three things enter the context: the video title, the video description, and a short
 * run of earlier subtitle units from the same track. Never the whole transcript. Every budget
 * is enforced here, and every length is counted in Unicode code points so a truncation cannot
 * split a surrogate pair. Subtitle text, titles, and descriptions are data: they are quoted
 * into a labelled reference block and the built-in prompt states that they must not be
 * followed as instructions.</p>
 */
public final class TranslationContextBuilder {
    public static final int MAX_TITLE_CHARS = 160;
    public static final int MAX_DESCRIPTION_CHARS = 640;
    public static final int MAX_HISTORY_UNITS = 3;
    /** Combined source + translation budget for the whole history block, in code points. */
    public static final int MAX_HISTORY_CHARS = 1200;
    /** Budget for the finished context string, labels included, in code points. */
    public static final int MAX_CONTEXT_CHARS = 2200;

    private static final String LABEL_TITLE = "Title: ";
    private static final String LABEL_DESCRIPTION = "Description: ";
    private static final String LABEL_HISTORY = "Earlier subtitles: ";
    private static final String ARROW = " => ";

    /** One earlier unit offered as reference; a null translation means "source only". */
    public static final class Entry {
        private final String mSourceText;
        private final String mTranslation;

        public Entry(String sourceText, String translation) {
            mSourceText = sourceText != null ? sourceText : "";
            mTranslation = translation;
        }

        public String getSourceText() {
            return mSourceText;
        }

        public String getTranslation() {
            return mTranslation;
        }
    }

    /**
     * Returns the bounded context for one request. An absent or blank title, description, or
     * history simply omits that section; the result is never null.
     */
    public String build(String title, String description, List<Entry> history) {
        StringBuilder context = new StringBuilder(256);

        appendSection(context, LABEL_TITLE, truncate(title, MAX_TITLE_CHARS));
        appendSection(context, LABEL_DESCRIPTION, truncate(description, MAX_DESCRIPTION_CHARS));
        appendHistory(context, history);

        return truncate(context.toString(), MAX_CONTEXT_CHARS);
    }

    private static void appendSection(StringBuilder context, String label, String value) {
        if (value == null || value.isEmpty()) return;

        if (context.length() > 0) context.append(' ');
        context.append(label).append(value);
    }

    private void appendHistory(StringBuilder context, List<Entry> history) {
        if (history == null || history.isEmpty()) return;

        List<String> lines = new ArrayList<>();
        int budget = MAX_HISTORY_CHARS;

        // Walk backwards so the newest reference wins when the budget runs out.
        for (int i = history.size() - 1; i >= 0 && lines.size() < MAX_HISTORY_UNITS; i--) {
            Entry entry = history.get(i);
            if (entry == null) continue;

            String line = renderEntry(entry);
            int cost = codePointCount(line);

            if (cost > budget) break;

            budget -= cost;
            lines.add(line);
        }

        if (lines.isEmpty()) return;

        Collections.reverse(lines);

        if (context.length() > 0) context.append(' ');
        context.append(LABEL_HISTORY);

        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) context.append(" | ");
            context.append(lines.get(i));
        }
    }

    private static String renderEntry(Entry entry) {
        String source = entry.getSourceText().trim();
        String translation = entry.getTranslation();

        if (translation == null || translation.trim().isEmpty()) return source;
        return source + ARROW + translation.trim();
    }

    /** Truncates to a code-point budget without splitting a surrogate pair. */
    static String truncate(String value, int maxCodePoints) {
        if (value == null) return null;
        if (maxCodePoints < 1) return "";

        String trimmed = value.trim();
        if (codePointCount(trimmed) <= maxCodePoints) return trimmed;

        int end = trimmed.offsetByCodePoints(0, maxCodePoints);
        return trimmed.substring(0, end);
    }

    private static int codePointCount(String value) {
        return value.codePointCount(0, value.length());
    }
}
