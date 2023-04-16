//package implementation;
//
//import com.intellij.openapi.project.Project;
//import service.ToolWindowServiceInterface;
//
//import javax.swing.*;
//
//public class EventManager {
//    private Project project;
//    private boolean initA;
//    private boolean initB;
//
//    public EventManager(Project project) {
//        this.project = project;
//    }
//
//    public void initA() {
//        Manager manager = project.getService(Manager.class);
//        manager.init(project);
//        System.out.println("initA");
//        this.initA = true;
////        initToolWindow();
//    }
//
//    public void initB() {
//
//        System.out.println("initB");
//        this.initB = true;
//        initToolWindow();
//    }
//
//    public void initToolWindow() {
////        if (this.initA && this.initB) {
////            SwingUtilities.invokeLater(() -> {
//        System.out.println("initToolWindow");
//        ToolWindowServiceInterface toolWindowService = project.getService(ToolWindowServiceInterface.class);
//        toolWindowService.addTab();
////            });
////        }
//    }
//}
