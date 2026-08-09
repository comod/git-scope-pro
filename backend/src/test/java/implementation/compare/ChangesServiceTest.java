package implementation.compare;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ChangesServiceTest {
    @Test
    public void extractsSelectedRefFromRangeScope() {
        assertEquals("main", ChangesService.getSelectedRef("main..HEAD"));
        assertEquals("origin/release", ChangesService.getSelectedRef("origin/release..HEAD"));
        assertEquals("HEAD~3", ChangesService.getSelectedRef("HEAD~3..HEAD"));
        assertEquals("abc123", ChangesService.getSelectedRef("abc123...HEAD"));
    }

    @Test
    public void rejectsScopesWithoutSelectedRef() {
        assertNull(ChangesService.getSelectedRef("HEAD"));
        assertNull(ChangesService.getSelectedRef("..HEAD"));
    }
}
