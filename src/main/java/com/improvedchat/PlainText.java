package com.improvedchat;

import javax.swing.AbstractButton;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;

final class PlainText
{
    private PlainText() { }
    static void set(JLabel label, String text)
    {
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setText(text);
    }
    static void set(AbstractButton button, String text)
    {
        button.putClientProperty("html.disable", Boolean.TRUE);
        button.setText(text);
    }
    static DefaultListCellRenderer renderer()
    {
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        return renderer;
    }
}
