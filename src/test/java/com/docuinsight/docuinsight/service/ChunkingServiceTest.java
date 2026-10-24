package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.service.ChunkingService.TextChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class ChunkingServiceTest {

    private final ChunkingService chunker = new ChunkingService();

    private static String longText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            sb.append("Sentence number ").append(i).append(" talks about the quarterly sales results in detail. ");
        }
        return sb.toString();
    }

    @Test
    void emptyOrNullTextGivesNoChunks() {
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("   \n  ")).isEmpty();
    }

    @Test
    void shortTextWithoutMarkersIsOneChunkWithoutPage() {
        List<TextChunk> chunks = chunker.chunk("name,role\nAlice,Engineer\n");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).page()).isNull();
        assertThat(chunks.get(0).text()).isEqualTo("name,role\nAlice,Engineer");
    }

    @Test
    void pageMarkersGiveEachChunkItsPageAndWindowsLineEndingsAreRemoved() {
        List<TextChunk> chunks = chunker.chunk("--- Page 1 ---\r\nRevenue grew\r\n\r\n--- Page 2 ---\r\nHiring plan\r\n");

        assertThat(chunks).extracting(TextChunk::page).containsExactly(1, 2);
        assertThat(chunks).extracting(TextChunk::text).containsExactly("Revenue grew", "Hiring plan");
        assertThat(chunks).allSatisfy(c -> assertThat(c.text()).doesNotContain("\r"));
    }

    @Test
    void longPageIsSplitIntoChunksOfAtMostChunkSize() {
        List<TextChunk> chunks = chunker.chunk("--- Page 3 ---\n" + longText());

        assertThat(chunks.size()).isGreaterThan(3);
        assertThat(chunks).allSatisfy(c -> {
            assertThat(c.text().length()).isLessThanOrEqualTo(ChunkingService.DEFAULT_CHUNK_SIZE);
            assertThat(c.page()).isEqualTo(3);
        });
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).index()).isEqualTo(i);
        }
    }

    @Test
    void neighbouringChunksOverlapAndEndAtSentences() {
        List<TextChunk> chunks = chunker.chunk(longText());

        String first = chunks.get(0).text();
        String secondStart = chunks.get(1).text().substring(0, 40);
        assertThat(first).contains(secondStart); // the overlap
        assertThat(first).endsWith(".");          // cut at a sentence, not mid-word
    }

    @Test
    void noTextIsLost() {
        List<TextChunk> chunks = chunker.chunk(longText());

        for (int i = 1; i <= 60; i++) {
            String sentence = "Sentence number " + i + " ";
            assertThat(chunks).anySatisfy(c -> assertThat(c.text()).contains(sentence));
        }
    }

    @Test
    void chunksNeverCrossPages() {
        List<TextChunk> chunks = chunker.chunk("--- Page 3 ---\n" + longText() + "\n--- Page 4 ---\nShort page.");

        TextChunk last = chunks.get(chunks.size() - 1);
        assertThat(last.page()).isEqualTo(4);
        assertThat(last.text()).isEqualTo("Short page.");
    }

    @Test
    void textWithoutSpacesStillFinishes() {
        List<TextChunk> chunks = chunker.chunk("x".repeat(2000));

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(3);
        assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(800));
    }

    @Test
    void textBeforeFirstMarkerHasNoPageAndInlineMarkerIsIgnored() {
        assertThat(chunker.chunk("Cover title\n--- Page 1 ---\nBody"))
                .extracting(TextChunk::page).containsExactly(null, 1);
        assertThat(chunker.chunk("See --- Page 5 --- inline"))
                .extracting(TextChunk::page).containsExactly((Integer) null);
    }

    @Test
    void invalidSizesAreRejected() {
        assertThatThrownBy(() -> chunker.chunk("abc", 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}