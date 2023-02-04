package implementation.targetBranchWidget;

import com.intellij.dvcs.ui.BranchActionGroup;
import com.intellij.dvcs.ui.LightActionGroup;
import com.intellij.dvcs.ui.PopupElementWithAdditionalInfo;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.PopupListElementRendererWithIcon;
import com.intellij.ui.ErrorLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.popup.KeepingPopupOpenAction;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.FontUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import implementation.Manager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;
import static icons.DvcsImplIcons.*;

public class MyBranchActionGroupPopup extends BranchActionGroupPopup {
//public class MyBranchActionGroupPopup extends FlatSpeedSearchPopup {
//  private static final DataKey MY_POPUP_MODEL = DataKey.create("GitScopeVcsPopupModel");
//  static final String BRANCH_POPUP = "GitScopeBranchWidget";

    private Project project;
    private MyPopupListElementRenderer myListElementRenderer;

    public MyBranchActionGroupPopup(@NotNull String title,
                                    @NotNull Project project,
                                    @NotNull Condition<AnAction> preselectActionCondition,
                                    @NotNull ActionGroup actions,
                                    @Nullable String dimensionKey,
                                    @NotNull DataContext dataContext
    ) {

        super(title, project, preselectActionCondition, actions, dimensionKey, dataContext);
        this.project = project;

    }

    @Override
    protected void handleToggleAction() {

    }

    @Override
    public void handleSelect(boolean handleFinalChoices, InputEvent e) {

        MyBranchAction myBranchAction = getSelectedMyBranchAction();
        if (myBranchAction != null) {

            if (e instanceof MouseEvent) {
                if (clickedAtIcon(((MouseEvent) e).getPoint())) {
                    myBranchAction.toggle();
                    getList().repaint();
                    return;
                }
            }

        }

        super.handleSelect(handleFinalChoices, e);

//    if (myBranchAction != null && e instanceof MouseEvent && myListElementRenderer.isIconAt(((MouseEvent)e).getPoint())) {
//      branchActionGroup.toggle();
//      getList().repaint();
//    }
//    else {
//      super.handleSelect(handleFinalChoices, e);
//    }
    }

    @Override
    protected boolean shouldBeShowing(@NotNull AnAction action) {
        if (!super.shouldBeShowing(action)) return false;
        if (getSpeedSearch().isHoldingFilter()) return !(action instanceof MyBranchActionGroupPopup.MoreAction);
        if (action instanceof MyBranchActionGroupPopup.MoreHideableActionGroup)
            return ((MyBranchActionGroupPopup.MoreHideableActionGroup) action).shouldBeShown();
        if (action instanceof MyGitBranchPopupActions.TargetBranchAction) {
            return !((MyGitBranchPopupActions.TargetBranchAction) action).getHide();
        }
        return true;
    }

    private boolean clickedAtIcon(Point point) {
        double x = point.getX();
        return x > 10 && x < 21;
    }

    //  @Nullable
    //  private BranchActionGroup getSelectedBranchGroup() {
    //    return getSpecificAction(getList().getSelectedValue(), BranchActionGroup.class);
    //  }

    @Nullable
    private MyBranchAction getSelectedMyBranchAction() {
        return getSpecificAction(getList().getSelectedValue(), MyBranchAction.class);
    }

    private static class MyPopupListElementRenderer extends PopupListElementRendererWithIcon {
        private ErrorLabel myInfoLabel;

        MyPopupListElementRenderer(ListPopupImpl aPopup) {
            super(aPopup);
        }

        @Override
        protected SeparatorWithText createSeparator() {
            return new MyTextSeparator();
        }

        @Override
        protected void customizeComponent(JList list, Object value, boolean isSelected) {
            MoreAction more = getSpecificAction(value, MoreAction.class);
            if (more != null) {
                myTextLabel.setForeground(JBColor.gray);
            }
            super.customizeComponent(list, value, isSelected);
            BranchActionGroup branchActionGroup = getSpecificAction(value, BranchActionGroup.class);
            if (branchActionGroup != null) {
                myTextLabel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
                myTextLabel.setIcon(chooseUpdateIndicatorIcon(branchActionGroup));
            }
            PopupElementWithAdditionalInfo additionalInfoAction = getSpecificAction(value, PopupElementWithAdditionalInfo.class);
            updateInfoComponent(myInfoLabel, additionalInfoAction != null ? additionalInfoAction.getInfoText() : null, isSelected);
        }

        private static Icon chooseUpdateIndicatorIcon(@NotNull BranchActionGroup branchActionGroup) {
            if (branchActionGroup.hasIncomingCommits()) {
                return branchActionGroup.hasOutgoingCommits() ? IncomingOutgoing : Incoming;
            }
            return branchActionGroup.hasOutgoingCommits() ? Outgoing : null;
        }

        private void updateInfoComponent(@NotNull ErrorLabel infoLabel, @Nullable String infoText, boolean isSelected) {
            if (infoText != null) {
                infoLabel.setVisible(true);
                infoLabel.setText(infoText);

                if (isSelected) {
                    setSelected(infoLabel);
                } else {
                    infoLabel.setBackground(getBackground());
                    infoLabel.setForeground(JBColor.GRAY);    // different foreground than for other elements
                }
            } else {
                infoLabel.setVisible(false);
            }
        }

        @Override
        protected JComponent createItemComponent() {
            myTextLabel = new ErrorLabel();
            myTextLabel.setOpaque(true);
            myTextLabel.setBorder(JBUI.Borders.empty(1));

            myInfoLabel = new ErrorLabel();
            myInfoLabel.setOpaque(true);
            myInfoLabel.setBorder(JBUI.Borders.empty(1, DEFAULT_HGAP, 1, 1));
            myInfoLabel.setFont(FontUtil.minusOne(myInfoLabel.getFont()));

            JPanel compoundPanel = new OpaquePanel(new BorderLayout(), JBColor.WHITE);
            myIconLabel = new IconComponent();
            myInfoLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            JPanel compoundTextPanel = new OpaquePanel(new BorderLayout(), compoundPanel.getBackground());
            JPanel textPanel = new OpaquePanel(new BorderLayout(), compoundPanel.getBackground());
            compoundPanel.add(myIconLabel, BorderLayout.WEST);
            textPanel.add(myTextLabel, BorderLayout.WEST);
            textPanel.add(myInfoLabel, BorderLayout.CENTER);
            compoundTextPanel.add(textPanel, BorderLayout.CENTER);
            compoundPanel.add(compoundTextPanel, BorderLayout.CENTER);
            return layoutComponent(compoundPanel);
        }
    }

    private static class MyTextSeparator extends SeparatorWithText {

        MyTextSeparator() {
            super();
            setTextForeground(UIUtil.getListForeground());
            setCaptionCentered(false);
            UIUtil.addInsets(this, DEFAULT_VGAP, UIUtil.getListCellHPadding(), 0, 0);
        }

        @Override
        protected void paintLine(Graphics g, int x, int y, int width) {
            if (StringUtil.isEmptyOrSpaces(getCaption())) {
                super.paintLine(g, x, y, width);
            }
        }
    }

    private static class MoreAction extends DumbAwareAction implements KeepingPopupOpenAction {

        @NotNull
        private final Project myProject;
        @Nullable
        private final String mySettingName;
        private final boolean myDefaultExpandValue;
        private boolean myIsExpanded;
        @NotNull
        private final String myToCollapseText;
        @NotNull
        private final String myToExpandText;

        MoreAction(@NotNull Project project,
                   int numberOfHiddenNodes,
                   @Nullable String settingName,
                   boolean defaultExpandValue,
                   boolean hasFavorites) {
            super();
            myProject = project;
            mySettingName = settingName;
            myDefaultExpandValue = defaultExpandValue;
            assert numberOfHiddenNodes > 0;
            myToExpandText = "Show " + numberOfHiddenNodes + " More...";
            myToCollapseText = "Show " + (hasFavorites ? "Only Favorites" : "Less");
            setExpanded(
                    settingName != null ? PropertiesComponent.getInstance(project).getBoolean(settingName, defaultExpandValue) : defaultExpandValue);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {

            setExpanded(!myIsExpanded);

            Manager manager = ServiceManager.getService(myProject, Manager.class);
            try {
                manager.getToolWindowUI().showTargetBranchPopup(e);
            } catch (Throwable ex) {
                // no
            }

        }

        public boolean isExpanded() {
            return myIsExpanded;
        }

        public void setExpanded(boolean isExpanded) {
            myIsExpanded = isExpanded;
            saveState();
            updateActionText();
        }

        private void updateActionText() {
            getTemplatePresentation().setText(myIsExpanded ? myToCollapseText : myToExpandText);
        }

        public void saveState() {
            // @todo save to own state, if problems continue
            if (mySettingName != null) {
                PropertiesComponent.getInstance(myProject).setValue(mySettingName, myIsExpanded, myDefaultExpandValue);
            }
        }
    }

    public static void _wrapWithMoreActionIfNeeded(
            @NotNull Project project,
            @NotNull LightActionGroup parentGroup,
            @NotNull List<MyGitBranchPopupActions.TargetBranchAction> actionList,
            int maxIndex,
            @Nullable String settingName,
            boolean defaultExpandValue
    ) {
        if (actionList.size() > maxIndex) {
            boolean hasFavorites = actionList.stream().anyMatch(action ->
                    action != null && action.isFavorite()
            );

            MoreAction moreAction = new MoreAction(project, actionList.size() - maxIndex, settingName, defaultExpandValue, hasFavorites);

            for (int i = 0; i < actionList.size(); i++) {
                // parentGroup.add(i < maxIndex ? actionList.get(i) : new BranchActionGroupPopup.HideableActionGroup(actionList.get(i), moreAction));

                MyGitBranchPopupActions.TargetBranchAction myBranchAction = actionList.get(i);

                if (i >= maxIndex && !moreAction.isExpanded()) {
                    myBranchAction.setHide(true);
                }

                ActionGroup actionGroup = new ActionGroup() {
                    @NotNull
                    @Override
                    public AnAction[] getChildren(@Nullable AnActionEvent e) {
                        return new AnAction[]{myBranchAction};
                    }
                };

                parentGroup.add(i < maxIndex ? myBranchAction : new HideableActionGroup(actionGroup, moreAction));
            }
            parentGroup.add(moreAction);
        } else {
            parentGroup.addAll(actionList);
        }
    }

    interface MoreHideableActionGroup {
        boolean shouldBeShown();
    }

    private static class HideableActionGroup extends EmptyAction.MyDelegatingActionGroup implements MoreHideableActionGroup, DumbAware {
        @NotNull
        private final MoreAction myMoreAction;

        private HideableActionGroup(@NotNull ActionGroup actionGroup, @NotNull MoreAction moreAction) {
            super(actionGroup);
            myMoreAction = moreAction;
        }

        @Override
        public boolean shouldBeShown() {
            return myMoreAction.isExpanded();
        }
    }

}
