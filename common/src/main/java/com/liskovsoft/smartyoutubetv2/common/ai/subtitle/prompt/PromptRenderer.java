package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Pure-JVM strict renderer for the fixed subtitle prompt variable catalog. */
public final class PromptRenderer {
    public RenderResult render(PromptProfile profile, Map<String, String> values) {
        if (profile == null) return RenderResult.invalid("prompt is missing");
        return render(profile.getContent(), values);
    }

    public RenderResult validate(String template) {
        return render(template, placeholderValues());
    }

    public RenderResult render(String template, Map<String, String> values) {
        if (template == null || template.trim().isEmpty()) {
            return RenderResult.invalid("prompt is empty");
        }
        if (values == null) values = Collections.emptyMap();

        StringBuilder output = new StringBuilder();
        List<String> diagnostics = new ArrayList<>();
        for (int i = 0; i < template.length();) {
            if (template.startsWith("\\{{", i)) {
                int literalEnd = template.indexOf("}}", i + 3);
                if (literalEnd < 0) {
                    diagnostics.add("malformed escaped variable delimiter at offset " + i);
                    break;
                }
                output.append("{{").append(template, i + 3, literalEnd).append("}}");
                i = literalEnd + 2;
            } else if (template.startsWith("\\}}", i)) {
                output.append("}}");
                i += 3;
            } else if (template.startsWith("{{", i)) {
                int end = template.indexOf("}}", i + 2);
                if (end < 0) {
                    diagnostics.add("malformed variable delimiter at offset " + i);
                    break;
                }
                String name = template.substring(i + 2, end).trim();
                PromptVariable variable = PromptVariable.fromName(name);
                if (variable == null) {
                    diagnostics.add("unknown variable: " + name);
                } else if (!values.containsKey(variable.getName())) {
                    diagnostics.add("missing value: " + variable.getName());
                } else {
                    // A present-but-empty value is a deliberate empty section, not a missing one.
                    output.append(values.get(variable.getName()));
                }
                i = end + 2;
            } else if (template.startsWith("}}", i)) {
                diagnostics.add("unmatched closing delimiter at offset " + i);
                i += 2;
            } else {
                output.append(template.charAt(i++));
            }
        }
        if (!diagnostics.isEmpty()) return RenderResult.invalid(diagnostics);
        return RenderResult.valid(output.toString());
    }

    private static Map<String, String> placeholderValues() {
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        for (PromptVariable variable : PromptVariable.values()) {
            values.put(variable.getName(), "<" + variable.getName() + ">");
        }
        return values;
    }

    public static final class RenderResult {
        private final String mText;
        private final List<String> mDiagnostics;

        private RenderResult(String text, List<String> diagnostics) {
            mText = text;
            mDiagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
        }
        static RenderResult valid(String text) { return new RenderResult(text, Collections.<String>emptyList()); }
        static RenderResult invalid(String diagnostic) {
            return invalid(Collections.singletonList(diagnostic));
        }
        static RenderResult invalid(List<String> diagnostics) {
            return new RenderResult("", diagnostics);
        }
        public boolean isValid() { return mDiagnostics.isEmpty(); }
        public String getText() { return mText; }
        public List<String> getDiagnostics() { return mDiagnostics; }
    }
}
