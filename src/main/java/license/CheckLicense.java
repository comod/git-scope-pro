package license;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.ui.LicensingFacade;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CheckLicense {
    private static final String PRODUCT_CODE = "PGITSCOPE";

    @Nullable
    public static Boolean isLicensed() {
        final LicensingFacade facade = LicensingFacade.getInstance();
        if (facade == null) {
            // Licensing facade not yet initialized - typically during startup
            return null;
        }

        try {
            // Check if we have a valid license for this product
            final String confirmationStamp = facade.getConfirmationStamp(PRODUCT_CODE);
            return confirmationStamp != null && !confirmationStamp.isEmpty();
        } catch (Exception e) {
            // In case of any error, assume unlicensed
            return false;
        }
    }

    public static void requestLicense(final String message) {
        // Use modern ModalityState.any() instead of deprecated NON_MODAL
        ApplicationManager.getApplication().invokeLater(() -> {
            // Trigger license check which may prompt user naturally
            final LicensingFacade facade = LicensingFacade.getInstance();
            if (facade != null) {
                try {
                    // Sometimes calling getConfirmationStamp triggers platform license prompts
                    facade.getConfirmationStamp(PRODUCT_CODE);
                } catch (Exception e) {
                    // Ignore errors
                }
            }

            // Show our own simple dialog about licensing
            showLicenseMessage(message);
        }, ModalityState.any());
    }

    private static void showLicenseMessage(String message) {
        try {
            SwingUtilities.invokeLater(() -> {
                String fullMessage = message + "\n\nPlease visit JetBrains Marketplace to purchase a license for Git Scope Pro.";

                JOptionPane.showMessageDialog(
                        null,
                        fullMessage,
                        "License Required - Git Scope Pro",
                        JOptionPane.WARNING_MESSAGE
                );
            });
        } catch (Exception e) {
            // Fallback: if dialog fails, just ignore
            // Don't let UI errors break the plugin
        }
    }

    public static void recheckLicense() {
        final LicensingFacade facade = LicensingFacade.getInstance();
        if (facade != null) {
            try {
                // Force refresh of license state
                facade.getConfirmationStamp(PRODUCT_CODE);
            } catch (Exception e) {
                // Ignore errors during recheck
            }
        }
    }
}