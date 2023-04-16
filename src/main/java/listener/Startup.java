//package listener;
//
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.startup.ProjectActivity;
//import com.intellij.openapi.startup.StartupActivity;
//import implementation.Manager;
//import kotlin.Unit;
//import kotlin.coroutines.Continuation;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//
//public class Startup implements ProjectActivity {
//
//    //    public class Startup implements BaseListenerInterface {
////    @Override
////    public void runActivity(@NotNull Project project) {
////
////        System.out.println("=====!start");
//
////    }
//
//    @Nullable
//    @Override
//    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
//        System.out.println("=====!start");
////        Manager manager = project.getService(Manager.class);
////        manager.init(project);
//        return null;
//    }
//}