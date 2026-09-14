package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation;

import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.TranslationContextBuilder.Entry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure-JVM budget and truncation tests for the bounded context builder. */
public class TranslationContextBuilderTest {
    private final TranslationContextBuilder mBuilder = new TranslationContextBuilder();

    @Test
    public void sectionsAppearInAFixedOrderAndOmitEmptyParts() {
        String context = mBuilder.build("A title", null,
                Collections.singletonList(new Entry("hello", "你好")));

        assertEquals("Title: A title Earlier subtitles: hello => 你好", context);
        assertFalse("an absent description must not add its label", context.contains("Description:"));
    }

    @Test
    public void anEmptyEverythingProducesAnEmptyContext() {
        assertEquals("", mBuilder.build(null, null, null));
        assertEquals("", mBuilder.build("   ", "  ", Collections.<Entry>emptyList()));
    }

    @Test
    public void titleAndDescriptionAreTruncatedToTheirBudgets() {
        StringBuilder title = new StringBuilder();
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < 700; i++) {
            title.append('t');
            description.append('d');
        }

        String context = mBuilder.build(title.toString(), description.toString(), null);

        assertTrue(context.contains("Title: " + repeat('t', TranslationContextBuilder.MAX_TITLE_CHARS)));
        assertTrue(context.contains("Description: " + repeat('d', TranslationContextBuilder.MAX_DESCRIPTION_CHARS)));
        assertFalse(context.contains(repeat('t', TranslationContextBuilder.MAX_TITLE_CHARS + 1)));
        assertFalse(context.contains(repeat('d', TranslationContextBuilder.MAX_DESCRIPTION_CHARS + 1)));
    }

    @Test
    public void historyKeepsTheNewestEntriesUpToItsUnitLimit() {
        List<Entry> history = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            history.add(new Entry("s" + i, "t" + i));
        }

        String context = mBuilder.build(null, null, history);

        assertFalse("the oldest entries must be dropped", context.contains("s0 "));
        assertFalse(context.contains("s2 =>"));
        assertTrue(context.contains("s3 => t3"));
        assertTrue(context.contains("s5 => t5"));
    }

    @Test
    public void historyWithoutATranslationCarriesOnlyTheSourceText() {
        String context = mBuilder.build(null, null,
                Arrays.asList(new Entry("first", "premier"), new Entry("second", null)));

        assertTrue(context.contains("first => premier"));
        assertTrue(context.contains("| second"));
        assertFalse(context.contains("second =>"));
    }

    @Test
    public void theHistoryBlockStaysInsideItsCharacterBudget() {
        List<Entry> history = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            history.add(new Entry(repeat('x', 200), repeat('y', 200)));
        }

        String context = mBuilder.build(null, null, history);
        String block = context.substring(context.indexOf("Earlier subtitles: "));

        assertTrue("history must stay within its own budget",
                block.codePointCount(0, block.length())
                        <= TranslationContextBuilder.MAX_HISTORY_CHARS + "Earlier subtitles: ".length());
    }

    @Test
    public void theWholeContextStaysInsideTheOverallBudget() {
        List<Entry> history = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            history.add(new Entry(repeat('x', 400), repeat('y', 400)));
        }

        String context = mBuilder.build(repeat('t', 500), repeat('d', 900), history);

        assertTrue(context.codePointCount(0, context.length())
                <= TranslationContextBuilder.MAX_CONTEXT_CHARS);
    }

    @Test
    public void truncationNeverSplitsASurrogatePair() {
        // A surrogate pair per code point: halving the budget must not produce a lone surrogate.
        String emoji = repeat("😀", 100);

        String truncated = TranslationContextBuilder.truncate(emoji, 10);

        assertEquals(10, truncated.codePointCount(0, truncated.length()));
        assertEquals(20, truncated.length());
        assertTrue("the result must remain a valid string", truncated.equals(repeat("😀", 10)));
    }

    @Test
    public void chineseTextIsCountedByCodePointNotByChar() {
        String chinese = repeat("字幕", 100);

        String truncated = TranslationContextBuilder.truncate(chinese, 7);

        assertEquals(7, truncated.codePointCount(0, truncated.length()));
        assertEquals(7, truncated.length());
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) builder.append(value);
        return builder.toString();
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) builder.append(value);
        return builder.toString();
    }
}
