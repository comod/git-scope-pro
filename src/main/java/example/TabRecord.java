package example;

import static java.util.stream.Collectors.joining;

public record TabRecord(String branchName) {
    public final static String head = "HEAD";
}
