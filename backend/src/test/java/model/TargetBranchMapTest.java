package model;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TargetBranchMapTest {

    @Test
    public void repoKeyIsRelativeToProjectBase() {
        assertEquals("repo", TargetBranchMap.repoKey("/home/user/project", "/home/user/project/repo"));
        assertEquals(".", TargetBranchMap.repoKey("/home/user/project", "/home/user/project"));
    }

    @Test
    public void repoKeyFallsBackToAbsolutePathWithoutACommonAncestor() {
        assertEquals("C:/repo", TargetBranchMap.repoKey(null, "C:/repo"));
        assertEquals("D:/repo", TargetBranchMap.repoKey("C:/project", "D:/repo"));
    }

    @Test
    public void resolvesByCurrentRelativeKey() {
        TargetBranchMap map = mapOf("repo", "main");
        assertEquals("main", map.resolve("repo", "/old/absolute/repo", 1));
    }

    @Test
    public void resolvesByLegacyAbsoluteKeyAndMigratesIt() {
        TargetBranchMap map = mapOf("/old/absolute/repo", "main");
        assertEquals("main", map.resolve("repo", "/old/absolute/repo", 1));
        // Self-healed: the next lookup (and the next save) uses the current key.
        assertEquals("main", map.value().get("repo"));
        assertTrue(!map.value().containsKey("/old/absolute/repo"));
    }

    @Test
    public void singleRepoProjectSelfHealsAnUnrecognizedKey() {
        // The repo moved outside the project's relative layout: neither key matches, but there's
        // exactly one repo and exactly one stored entry, so it must be this repo's.
        TargetBranchMap map = mapOf("/some/unrelated/old/path", "main");
        assertEquals("main", map.resolve("repo", "/current/absolute/repo", 1));
        assertEquals("main", map.value().get("repo"));
    }

    @Test
    public void doesNotGuessInMultiRepoProjectsWithNoKeyMatch() {
        TargetBranchMap map = mapOf("/some/unrelated/old/path", "main");
        assertNull(map.resolve("repo", "/current/absolute/repo", 2));
    }

    @Test
    public void doesNotGuessWhenMultipleEntriesAreStored() {
        Map<String, String> entries = new HashMap<>();
        entries.put("/unrelated/one", "main");
        entries.put("/unrelated/two", "develop");
        TargetBranchMap map = new TargetBranchMap(entries);
        assertNull(map.resolve("repo", "/current/absolute/repo", 1));
    }

    private static TargetBranchMap mapOf(String key, String branch) {
        Map<String, String> entries = new HashMap<>();
        entries.put(key, branch);
        return new TargetBranchMap(entries);
    }
}
