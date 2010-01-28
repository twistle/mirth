package com.webreach.mirth.manager.components;

import com.webreach.mirth.manager.ManagerController;
import com.webreach.mirth.manager.PlatformUI;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * Mirth's implementation of the JComboBox. Adds enabling of the apply button in
 * dialog.
 */
public class MirthComboBox extends javax.swing.JComboBox {

    public MirthComboBox() {
        super();
        this.setFocusable(true);
        this.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboBoxChanged(evt);
            }
        });
        this.addKeyListener(new KeyListener() {

            public void keyPressed(KeyEvent e) {
                boolean isAccelerated = (e.getModifiers() & java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) > 0;
                if ((e.getKeyCode() == KeyEvent.VK_S) && isAccelerated) {
                    PlatformUI.MANAGER_DIALOG.saveProperties();
                }
            }

            public void keyReleased(KeyEvent e) {
                // TODO Auto-generated method stub
            }

            public void keyTyped(KeyEvent e) {
                // TODO Auto-generated method stub
            }
        });
    }

    public void comboBoxChanged(java.awt.event.ActionEvent evt) {
        ManagerController.getInstance().setApplyEnabled(true);
    }
}