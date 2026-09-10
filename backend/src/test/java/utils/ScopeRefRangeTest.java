package utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ScopeRefRangeTest {

    @Test
    public void detectsRangeSyntax() {
        assertTrue(ScopeRefRange.isRange("main..HEAD"));
        assertTrue(ScopeRefRange.isRange("main...HEAD"));
        assertTrue(ScopeRefRange.isRange("v1.0..v2.0"));
        assertFalse(ScopeRefRange.isRange("main"));
        assertFalse(ScopeRefRange.isRange("HEAD~3"));
        assertFalse(ScopeRefRange.isRange("release-1.2"));
        assertFalse(ScopeRefRange.isRange(null));
    }

    @Test
    public void extractsSelectedRefFromSupportedRanges() {
        assertEquals("main", ScopeRefRange.selectedRef("main..HEAD"));
        assertEquals("main", ScopeRefRange.selectedRef("main...HEAD"));
        assertEquals("origin/release", ScopeRefRange.selectedRef("origin/release...HEAD"));
        assertEquals("HEAD~3", ScopeRefRange.selectedRef("HEAD~3..HEAD"));
        assertEquals("abc123", ScopeRefRange.selectedRef("abc123...HEAD"));
        assertEquals("feature/a.b", ScopeRefRange.selectedRef("feature/a.b...HEAD"));
        // Git defaults an omitted side to HEAD
        assertEquals("main", ScopeRefRange.selectedRef("main.."));
        assertEquals("main", ScopeRefRange.selectedRef("main..."));
    }

    @Test
    public void rejectsRangesThatDoNotTargetHead() {
        assertNull(ScopeRefRange.selectedRef("v1.0..v2.0"));
        assertNull(ScopeRefRange.selectedRef("main...feature"));
        assertNull(ScopeRefRange.selectedRef("a..b..HEAD"));
        assertNull(ScopeRefRange.selectedRef("main....HEAD"));
    }

    @Test
    public void rejectsScopesWithoutSelectedRef() {
        assertNull(ScopeRefRange.selectedRef("..HEAD"));
        assertNull(ScopeRefRange.selectedRef("...HEAD"));
        assertNull(ScopeRefRange.selectedRef("HEAD"));
        assertNull(ScopeRefRange.selectedRef("main"));
        assertNull(ScopeRefRange.selectedRef(null));
    }

    @Test
    public void stripsSupportedRangesOnly() {
        assertEquals("main", ScopeRefRange.stripRange("main...HEAD"));
        assertEquals("main", ScopeRefRange.stripRange("main..HEAD"));
        assertEquals("main", ScopeRefRange.stripRange("main"));
        assertEquals("v1.0..v2.0", ScopeRefRange.stripRange("v1.0..v2.0"));
    }
}
