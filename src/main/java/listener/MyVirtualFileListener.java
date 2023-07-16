package listener;

import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import org.jetbrains.annotations.NotNull;

public class MyVirtualFileListener implements VirtualFileListener {

    public void contentsChanged(@NotNull VirtualFileEvent event) {
        // nothing happens?
        //System.out.println("contentsChanged" + event);
    }
}
