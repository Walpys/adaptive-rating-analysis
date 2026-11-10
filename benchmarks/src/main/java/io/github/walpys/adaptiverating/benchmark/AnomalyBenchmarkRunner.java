package io.github.walpys.adaptiverating.benchmark;

import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;

public class AnomalyBenchmarkRunner {

    private static final String REPORT_DIRECTORY =
            "benchmarks/target/jmh-reports";

    private static final String REPORT_NAME =
            "jmh-results.json";

    public static void main(String[] args) throws Exception {

        System.out.println("======================================");
        System.out.println("       ANOMALY DETECTION BENCHMARK");
        System.out.println("======================================");

        File reportsDir = new File(REPORT_DIRECTORY);

        if (!reportsDir.exists() && !reportsDir.mkdirs()) {
            throw new IllegalStateException(
                    "Could not create reports directory: "
                            + reportsDir.getAbsolutePath()
            );
        }

        File reportFile = new File(reportsDir, REPORT_NAME);

        System.out.println("Working directory: "
                + System.getProperty("user.dir"));

        System.out.println("Report location: "
                + reportFile.getAbsolutePath());

        System.out.println("Starting benchmarks...\n");

        Options options = new OptionsBuilder()

                .include(
                        AnomalyDetectionBenchmark.class.getSimpleName()
                )

                .addProfiler(GCProfiler.class)

                .result(reportFile.getAbsolutePath())

                .resultFormat(ResultFormatType.JSON)

                .shouldFailOnError(true)

                .build();

        new Runner(options).run();

        System.out.println("\n======================================");
        System.out.println("       BENCHMARKS COMPLETED");
        System.out.println("======================================");

        System.out.println("JSON report saved successfully:");
        System.out.println(reportFile.getAbsolutePath());
    }
}