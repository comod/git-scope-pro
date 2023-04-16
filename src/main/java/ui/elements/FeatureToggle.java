package ui.elements;

import eu.hansolo.custom.SteelCheckBox;
import implementation.Manager;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class FeatureToggle extends SteelCheckBox implements Element {

    public FeatureToggle() {

        this.createElement();
        this.addListener();

    }

    public void createElement() {

        String text = "Toggle between HEAD and target branch (ALT + H)";
        this.setToolTipText(text);

    }

    public void addListener() {

//        this.addActionListener(e -> {
//            manager.doCompareAndUpdate();
//        });

    }

}
