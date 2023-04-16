//package system;
//
//import com.intellij.openapi.application.Application;
//import com.intellij.openapi.application.ApplicationManager;
//import com.intellij.openapi.components.BaseComponent;
//import com.intellij.openapi.project.ProjectManager;
//
//
//public class OnStart implements BaseComponent {
//
//    @Override
//    public void initComponent() {
//
//        Application application = ApplicationManager.getApplication();
//        MyProjectManagerListener projectManagerListener = new MyProjectManagerListener();
//        application.getMessageBus().connect().subscribe(ProjectManager.TOPIC, projectManagerListener);
//
//    }
//
//}
