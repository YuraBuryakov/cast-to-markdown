package io.github.yuraburyakov.casttomarkdown;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** One shared instance used from many threads, as in a Spring singleton bean. */
class CastToMarkdownConcurrencyTest {

    private static final int THREADS = 8;
    private static final int CALLS_PER_THREAD = 20;

    @TempDir
    Path dir;

    @Test
    @Timeout(60)
    void sharedInstanceGivesSameResultFromManyThreads() throws Exception {
        Path first = pdf("first.pdf", "Alpha");
        Path second = pdf("second.pdf", "Beta");
        CastToMarkdown converter = CastToMarkdown.create();
        String expectedFirst = converter.convert(first).markdown();
        String expectedSecond = converter.convert(second).markdown();
        assertThat(expectedFirst).contains("Alpha page 3, paragraph two.").contains("\n\n");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> results = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                boolean useFirst = t % 2 == 0;
                results.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < CALLS_PER_THREAD; i++) {
                        assertThat(converter.convert(useFirst ? first : second).markdown())
                                .isEqualTo(useFirst ? expectedFirst : expectedSecond);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(); // rethrows an assertion error or exception from the worker
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    /** Several pages with several paragraphs, so the paragraph detection is exercised too. */
    private Path pdf(String name, String word) throws IOException {
        TestPdf builder = TestPdf.builder();
        for (int page = 1; page <= 3; page++) {
            builder.page()
                    .line(720, word + " page " + page + ", paragraph one, line one.")
                    .line(706, word + " page " + page + ", paragraph one, line two.")
                    .line(660, word + " page " + page + ", paragraph two.");
        }
        return builder.writeTo(dir.resolve(name));
    }
}
