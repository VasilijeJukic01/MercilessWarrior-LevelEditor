package editor.gui.view.style;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

import static editor.constants.Constants.*;

/**
 * Custom flat button UI used across editor toolbars.
 */
public class CustomButton extends BasicButtonUI {

    @Override
    protected void installDefaults(AbstractButton b) {
        super.installDefaults(b);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 1, TAB_TOOLBAR_COLOR),
                BorderFactory.createEmptyBorder(7, 9, 7, 9)
        ));
        b.setBackground(BUTTON_COLOR);
        b.setForeground(Color.WHITE);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 12f));
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        AbstractButton button = (AbstractButton) c;
        ButtonModel model = button.getModel();

        if (model.isPressed()) g.setColor(BUTTON_PRESS_COLOR);
        else if (model.isRollover()) g.setColor(BUTTON_HOVER_COLOR);
        else g.setColor(button.getBackground());

        g.fillRect(0, 0, c.getWidth(), c.getHeight());
        super.paint(g, c);
    }
}