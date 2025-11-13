package com.universaldiff.core.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoreModelValueObjectTest {

    @Test
    void diffFragmentNullContentDefaultsToEmptyString() {
        DiffFragment fragment = new DiffFragment(DiffSide.LEFT, 0, 0, null);
        assertThat(fragment.getContent()).isEmpty();
    }

    @Test
    void diffHunkNullSummaryBecomesEmpty() {
        DiffHunk hunk = DiffHunk.of("id", DiffType.MODIFY, null,
                List.of(new DiffFragment(DiffSide.LEFT, 0, 0, "value")));
        assertThat(hunk.getSummary()).isEmpty();
    }

    @Test
    void diffResultAndMergeResultDefaultDuration() {
        DiffResult diffResult = new DiffResult(FormatType.TXT, List.of(), null);
        MergeResult mergeResult = new MergeResult(FormatType.TXT, null, null);

        assertThat(diffResult.getExecutionTime()).isEqualTo(Duration.ZERO);
        assertThat(mergeResult.getExecutionTime()).isEqualTo(Duration.ZERO);
    }

    @Test
    void fileDescriptorDefaultsToUtf8() {
        FileDescriptor descriptor = new FileDescriptor(PathTestUtils.somePath(), FormatType.TXT, null);
        assertThat(descriptor.getEncoding()).isEqualTo(StandardCharsets.UTF_8);
    }

    private static final class PathTestUtils {
        private static java.nio.file.Path somePath() {
            return java.nio.file.Paths.get("virtual.txt");
        }
    }
}
