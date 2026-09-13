package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PromptRendererTest {
    @Test
    public void rendersRepeatedVariablesAndEscapedLiteralDelimiters() {
        Map<String, String> values = values();
        PromptRenderer.RenderResult result = new PromptRenderer().render(
                "{{source_text}} / {{source_text}} / \\{{source_text}}", values);
        assertTrue(result.isValid());
        assertEquals("Hello / Hello / {{source_text}}", result.getText());
    }

    @Test
    public void unknownMissingAndMalformedVariablesAreExplicitDiagnostics() {
        PromptRenderer renderer = new PromptRenderer();
        PromptRenderer.RenderResult unknown = renderer.render("{{unknown}}", values());
        assertFalse(unknown.isValid());
        assertTrue(unknown.getDiagnostics().get(0).contains("unknown variable"));

        Map<String, String> missing = values();
        missing.remove("target_language");
        assertTrue(renderer.render("{{target_language}}", missing).getDiagnostics().get(0)
                .contains("missing value"));
        assertFalse(renderer.render("{{source_text}", values()).isValid());
    }

    @Test
    public void validationUsesCatalogWithoutLeakingPromptValues() {
        PromptRenderer.RenderResult result = new PromptRenderer().validate(
                "Translate {{source_text}} to {{target_language}}");
        assertTrue(result.isValid());
        assertFalse(result.getText().contains("value"));
    }

    private static Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("source_text", "Hello");
        values.put("source_language", "en");
        values.put("target_language", "zh");
        values.put("context", "nearby");
        values.put("unit_index", "3");
        return values;
    }
}
