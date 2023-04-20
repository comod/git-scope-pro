package example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Calendar;

import com.intellij.openapi.project.Project;
import implementation.targetBranchWidget.PopUpFactory;
import model.MyModel;
import org.apache.commons.lang3.StringUtils;

import service.TargetBranchService;
import ui.elements.VcsTree;

public class ToolWindowView {

    private final JPanel contentPanel = new JPanel();

    private final JLabel currentTime = new JLabel();
    //    private final State state;
    private final MyModel myModel;
    private final Project project;
    private final TargetBranchService targetBranchService;

    JButton tbp = new JButton();

    private VcsTree vcsTree;

    public ToolWindowView(Project project, MyModel myModel) {
        this.project = project;
//        this.state = State.getInstance(project);
        this.myModel = myModel;
        this.targetBranchService = project.getService(TargetBranchService.class);
        myModel.getObservable().subscribe(model -> {
            render();
        });
        contentPanel.setLayout(new BorderLayout(0, 0));
//        contentPanel.setLayout(new FlowLayout());
//        contentPanel.setBorder(null);
//        contentPanel.add(createCalendarPanel(), BorderLayout.PAGE_START);
//        JPanel topPanel = new JPanel();
//        topPanel.add(tbp);
//        contentPanel.add(topPanel);
        JPanel controlsPanel = new JPanel();
//        JButton refreshDateAndTimeButton = new JButton("Refresh");
//        refreshDateAndTimeButton.addActionListener(e -> someAction());
//        controlsPanel.add(refreshDateAndTimeButton);
//        JButton hideToolWindowButton = new JButton("Hide");
////        hideToolWindowButton.addActionListener(e -> toolWindow.hide(null));
//        controlsPanel.add(hideToolWindowButton);

//        FeatureToggle featureToggle = new FeatureToggle();
//        controlsPanel.add(featureToggle);

        this.vcsTree = new VcsTree(this.project);
        this.vcsTree.setLayout(new FlowLayout());
        controlsPanel.setLayout(new FlowLayout());
        controlsPanel.add(tbp);
        controlsPanel.add(vcsTree);
//        TargetBranchPanel tbp = new TargetBranchPanel(this.project);
        addListener();

        contentPanel.add(controlsPanel);
//        ToolWindowUI toolWindowUI = new ToolWindowUI(project);
//        contentPanel.add(toolWindowUI.getRootPanel());
        render();
    }

    private void addListener() {
        tbp.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                PopUpFactory f = project.getService(PopUpFactory.class);
                f.showPopup(e);
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent) {

            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent) {

            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent) {

            }

            @Override
            public void mouseExited(MouseEvent mouseEvent) {

            }
        });
    }


    private void someAction() {
        Calendar calendar = Calendar.getInstance();

        String s =
                getFormattedValue(calendar, Calendar.HOUR_OF_DAY) + ":" +
                        getFormattedValue(calendar, Calendar.MINUTE) + ":" +
                        getFormattedValue(calendar, Calendar.SECOND);
        myModel.setField1(s);

    }

    private void render() {
        currentTime.setText(myModel.getField1());
        tbp.setText(this.targetBranchService.getTargetBranchDisplay(myModel.getTargetBranch()));
        if (myModel.getChanges() == null) {
            return;
        }
        vcsTree.update(myModel.getChanges());
    }

    private String getFormattedValue(Calendar calendar, int calendarField) {
        int value = calendar.get(calendarField);
        return StringUtils.leftPad(Integer.toString(value), 2, "0");
    }

    public JPanel getContentPanel() {
        return contentPanel;
    }
}
