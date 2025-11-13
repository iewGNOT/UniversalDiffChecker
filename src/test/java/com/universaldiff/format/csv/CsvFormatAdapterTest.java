package com.universaldiff.format.csv;

import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.DiffType;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeChoice;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import com.universaldiff.core.model.NormalizedContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class CsvFormatAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void diff_handlesQuotedFieldsAndEscapes() throws Exception {
        String leftCsv = "id,name,notes\n" +
                "1,\"Alice, A.\",\"Line1\nLine2\"\n" +
                "2,Bob,\"Quote \"\"value\"\"\"\n";
        String rightCsv = "id,name,notes\n" +
                "1,\"Alice, A.\",\"Line1\nUpdated\"\n" +
                "2,Bob,\"Quote \"\"value\"\"\"\n" +
                "3,Carol,\"New row\"\n";

        Path left = Files.writeString(tempDir.resolve("left.csv"), leftCsv, StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.csv"), rightCsv, StandardCharsets.UTF_8);

        CsvFormatAdapter adapter = new CsvFormatAdapter();
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.CSV, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.CSV, StandardCharsets.UTF_8));

        DiffResult diff = adapter.diff(leftContent, rightContent);

        assertThat(diff.getHunks())
                .extracting(h -> decodeKey(h.getId()), DiffHunk::getType)
                .containsExactlyInAnyOrder(
                        tuple("1", DiffType.MODIFY),
                        tuple("3", DiffType.INSERT)
                );
    }

    @Test
    void merge_takeRightMatchesRightFile() throws Exception {
        String leftCsv = "id,color\n10,red\n20,blue\n";
        String rightCsv = "id,color\n10,red\n20,green\n30,yellow\n";

        Path left = Files.writeString(tempDir.resolve("left.csv"), leftCsv, StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.csv"), rightCsv, StandardCharsets.UTF_8);
        Path output = tempDir.resolve("merged.csv");

        CsvFormatAdapter adapter = new CsvFormatAdapter();
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.CSV, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.CSV, StandardCharsets.UTF_8));
        DiffResult diff = adapter.diff(leftContent, rightContent);

        List<MergeDecision> decisions = diff.getHunks().stream()
                .map(h -> new MergeDecision(h.getId(), MergeChoice.TAKE_RIGHT, null))
                .toList();

        MergeResult result = adapter.merge(leftContent, rightContent, decisions, output);
        assertThat(result.getOutputPath()).isEqualTo(output);
        assertThat(Files.readAllLines(output, StandardCharsets.UTF_8))
                .isEqualTo(Files.readAllLines(right, StandardCharsets.UTF_8));
    }

    @Test
    void diff_withoutHeadersUsesGeneratedColumnNames() throws Exception {
        String leftCsv = "1,alpha\n2,beta\n";
        String rightCsv = "1,alpha\n2,gamma\n3,delta\n";

        Path left = Files.writeString(tempDir.resolve("left.csv"), leftCsv, StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.csv"), rightCsv, StandardCharsets.UTF_8);

        CsvFormatAdapter adapter = new CsvFormatAdapter();
        DiffResult diff = adapter.diff(
                adapter.normalize(new FileDescriptor(left, FormatType.CSV, StandardCharsets.UTF_8)),
                adapter.normalize(new FileDescriptor(right, FormatType.CSV, StandardCharsets.UTF_8))
        );

        assertThat(diff.getHunks()).hasSize(2);
        assertThat(diff.getHunks().get(0).getFragments())
                .allSatisfy(fragment -> assertThat(fragment.getContent()).contains("col1"));
    }

    @Test
    void merge_manualContentIsParsedAndPadded() throws Exception {
        String leftCsv = "id,name,notes\n1,Alpha,old\n";
        String rightCsv = "id,name,notes\n1,Beta,new\n";
        Path left = Files.writeString(tempDir.resolve("manual-left.csv"), leftCsv, StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("manual-right.csv"), rightCsv, StandardCharsets.UTF_8);
        Path output = tempDir.resolve("manual-output.csv");

        CsvFormatAdapter adapter = new CsvFormatAdapter();
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.CSV, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.CSV, StandardCharsets.UTF_8));
        DiffResult diff = adapter.diff(leftContent, rightContent);

        MergeDecision manual = new MergeDecision(
                diff.getHunks().get(0).getId(),
                MergeChoice.MANUAL,
                "id=1 | name=ManualOnly" // intentionally omit notes value
        );

        adapter.merge(leftContent, rightContent, List.of(manual), output);

        assertThat(Files.readAllLines(output, StandardCharsets.UTF_8))
                .containsExactly("id,name,notes", "1,ManualOnly,");
    }

    private String decodeKey(String hunkId) {
        String encoded = hunkId.replace("csv-row-", "");
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}
