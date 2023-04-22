package listener;

import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;

public class MyTabContentListener implements ContentManagerListener {
    public void contentAdded(@NotNull ContentManagerEvent event) {
        System.out.println("contentAdded");
    }

    public void selectionChanged(@NotNull ContentManagerEvent event) {
        if (event.getOperation().equals(ContentManagerEvent.ContentOperation.add)) {
            System.out.println(event.getIndex());
        }
    }
}
