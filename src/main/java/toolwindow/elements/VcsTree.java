package toolwindow.elements;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;

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
    }

    public void addListener() {
    }

    public void setLoading() {
        ApplicationManager.getApplication().invokeLater(() -> 
            this.setComponent(centeredLoadingPanel(loadingIcon()))
        );
    }

    public void update(Collection<Change> changes) {
        setLoading();
        
        // Create and update browser in EDT
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) {
                SimpleChangesBrowser changesBrowser = new MySimpleChangesBrowser(project, changes);
                setComponent(changesBrowser);
            }
        });
    }

    private void setComponent(Component component) {
        // Since this is called from invokeLater, we're already on EDT
        for (Component c : this.getComponents()) {
            this.remove(c);
        }
        this.add(component);
        this.revalidate();
        this.repaint();
    }

    private JPanel centeredLoadingPanel(Component component) {
        JPanel masterPane = new JPanel(new GridBagLayout());
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