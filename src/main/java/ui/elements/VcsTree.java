package ui.elements;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import ui.MySimpleChangesBrowser;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

public class VcsTree extends JPanel {

    private final Project project;

    public VcsTree(Project project) {

        this.project = project;

        this.createElement();
        this.addListener();

    }

    public void createElement() {
//        this.setLayout(new BorderLayout());

        this.setLoading();

    }

    public void addListener() {

    }

    public void setLoading() {
        this.setComponent(centeredLoadingPanel(loadingIcon()));
    }

    public void update(Collection<Change> changes) {

        // Build the Diff-Tool-Window
        // SimpleChangesBrowser changesBrowser = new SimpleChangesBrowser(
        SimpleChangesBrowser changesBrowser = new MySimpleChangesBrowser(
                project,
                changes
        );

        setComponent(changesBrowser);

    }

    public void setComponent(Component component) {

        for (Component c : this.getComponents()) {
            this.remove(c);
        }

        this.add(component);

    }

    public JPanel centeredLoadingPanel(Component component) {
        JPanel masterPane = new JPanel(new GridBagLayout());

        JPanel centerPane = new JPanel();
        centerPane.setLayout(new BoxLayout(centerPane, BoxLayout.Y_AXIS));

        masterPane.add(component);

        return masterPane;
    }

    private Component loadingIcon() {

        ClassLoader cldr = this.getClass().getClassLoader();
        java.net.URL imageURL = cldr.getResource("loading.png");
        assert imageURL != null;
        ImageIcon imageIcon = new ImageIcon(imageURL);
        JLabel iconLabel = new JLabel();
        iconLabel.setIcon(imageIcon);
        imageIcon.setImageObserver(iconLabel);

        return iconLabel;

    }

}
