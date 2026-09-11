package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.support;

import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;

/**
 * Robolectric 4.6.1 crashes on JDK > 16: its bundled ASM rejects class file major
 * version 61 while instrumenting shadow classes ("Unsupported class file major
 * version 61" raised from {@code Shadows.reset()} in {@code finallyAfterTest}).
 * See {@code docs/ai-subtitle/worker-reports/M02-report.md}.
 *
 * <p>This runner keeps Robolectric tests present and correct: on incompatible JVMs
 * they are reported as ignored (never as failures), and on compatible JVMs
 * (JDK 16 and below) they run normally through the regular Robolectric flow.</p>
 */
public class JdkAwareRobolectricRunner extends RobolectricTestRunner {
    private static final int MAX_SUPPORTED_JDK_MAJOR = 16;

    public JdkAwareRobolectricRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    public void run(RunNotifier notifier) {
        if (currentJdkMajor() > MAX_SUPPORTED_JDK_MAJOR) {
            // Do not enter the Robolectric sandbox at all on unsupported JVMs.
            for (Description description : getDescription().getChildren()) {
                notifier.fireTestIgnored(description);
            }
            return;
        }

        super.run(notifier);
    }

    private static int currentJdkMajor() {
        String spec = System.getProperty("java.specification.version", "17");

        try {
            return spec.startsWith("1.") ? Integer.parseInt(spec.substring(2)) : Integer.parseInt(spec);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
