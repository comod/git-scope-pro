package listener;

import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MyVfsListener implements BulkFileListener {
//    @Override
//    public void before(@NotNull List<? extends VFileEvent> events) {
//        //System.out.println("=====!MyVfsListener(before)" + events);
//    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        // update head generally
        //System.out.println("=====!MyVfsListener(after)" + events);
    }
}
