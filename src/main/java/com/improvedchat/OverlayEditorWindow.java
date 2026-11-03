package com.improvedchat;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

class OverlayEditorWindow {
    private JDialog dialog;

    void open(Component owner, JComponent content, String title, Runnable closed) {
        if (dialog != null && !dialog.isDisplayable()) close();
        if (dialog == null) {
            dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), title, Dialog.ModalityType.MODELESS);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setContentPane(content);
            dialog.setMinimumSize(new Dimension(420, 400));
            dialog.setSize(560, 720);
            dialog.setLocationRelativeTo(owner);
            JDialog opened = dialog;
            dialog.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent event) {
                    if (dialog != opened) return;
                    dialog = null;
                    closed.run();
                }
            });
        }
        title(title);
        dialog.setVisible(true);
        dialog.toFront();
    }

    void title(String title) {
        if (dialog != null) dialog.setTitle(title);
    }

    void close() {
        JDialog previous = dialog;
        dialog = null;
        if (previous != null) previous.dispose();
    }
}
