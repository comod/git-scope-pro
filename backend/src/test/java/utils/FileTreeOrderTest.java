package utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileTreeOrderTest {

    private static List<String> sorted(String... paths) {
        List<String> list = new ArrayList<>(Arrays.asList(paths));
        list.sort(FileTreeOrder.INSTANCE);
        return list;
    }

    @Test
    public void walksSubdirectoriesBeforeFilesBesideThem() {
        assertEquals(
                Arrays.asList(
                        "/p/src/sub/a.java",
                        "/p/src/sub/b.java",
                        "/p/src/aaa.java",
                        "/p/src/zzz.java"),
                sorted(
                        "/p/src/zzz.java",
                        "/p/src/sub/b.java",
                        "/p/src/aaa.java",
                        "/p/src/sub/a.java"));
    }

    @Test
    public void ordersNamesCaseInsensitively() {
        /* Plain string ordering puts every capitalised name first, which is what made navigation
           look erratic: Zebra.java would come before apple.java. */
        assertEquals(
                Arrays.asList("/p/apple.java", "/p/Banana.java", "/p/Zebra.java"),
                sorted("/p/Zebra.java", "/p/apple.java", "/p/Banana.java"));
    }

    @Test
    public void ordersDigitRunsNumerically() {
        assertEquals(
                Arrays.asList("/p/file2.java", "/p/file10.java"),
                sorted("/p/file10.java", "/p/file2.java"));
    }

    @Test
    public void keepsSiblingDirectoriesSeparate() {
        /* "/p/a.java" must not land between the contents of "/p/a/", which is what happens when
           full paths are compared as strings ('.' sorts before '/'). */
        assertEquals(
                Arrays.asList(
                        "/p/a/one.java",
                        "/p/a/two.java",
                        "/p/b/one.java",
                        "/p/a.java"),
                sorted(
                        "/p/a.java",
                        "/p/b/one.java",
                        "/p/a/two.java",
                        "/p/a/one.java"));
    }

    @Test
    public void ordersDeepAndShallowConsistently() {
        assertEquals(
                Arrays.asList(
                        "/p/src/main/java/App.java",
                        "/p/src/main/resources/app.xml",
                        "/p/src/test/AppTest.java",
                        "/p/src/build.gradle",
                        "/p/README.md"),
                sorted(
                        "/p/README.md",
                        "/p/src/build.gradle",
                        "/p/src/test/AppTest.java",
                        "/p/src/main/resources/app.xml",
                        "/p/src/main/java/App.java"));
    }

    @Test
    public void isAntisymmetricAndStable() {
        String a = "/p/src/sub/a.java";
        String b = "/p/src/b.java";
        assertTrue(FileTreeOrder.INSTANCE.compare(a, b) < 0);
        assertTrue(FileTreeOrder.INSTANCE.compare(b, a) > 0);
        assertEquals(0, FileTreeOrder.INSTANCE.compare(a, a));
    }

    @Test
    public void distinguishesPathsDifferingOnlyByCase() {
        // Natural comparison reports these equal; a TreeSet would then drop one of the two files.
        assertTrue(FileTreeOrder.INSTANCE.compare("/p/File.java", "/p/file.java") != 0);
    }
}
