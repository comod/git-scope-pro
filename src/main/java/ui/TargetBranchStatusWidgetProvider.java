//// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//package ui;
//
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.wm.StatusBar;
//import com.intellij.openapi.wm.StatusBarWidget;
//import com.intellij.openapi.wm.StatusBarWidgetProvider;
//import org.jetbrains.annotations.NotNull;
//
//public class TargetBranchStatusWidgetProvider implements StatusBarWidgetProvider {
//
//  @NotNull
//  @Override
//  public StatusBarWidget getWidget(@NotNull Project project) {
//    ui.StatusBar statusBar = new ui.StatusBar(project);
//    statusBar.activate();
//    return statusBar;
//  }
//
//  @NotNull
//  @Override
//  public String getAnchor() {
//    return StatusBar.Anchors.before(StatusBar.StandardWidgets.READONLY_ATTRIBUTE_PANEL);
//  }
//}
