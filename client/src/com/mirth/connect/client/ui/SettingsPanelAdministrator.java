/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.ui;

import java.util.prefs.Preferences;

import javax.swing.SwingWorker;

import org.apache.commons.lang3.BooleanUtils;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.components.MirthFieldConstraints;
import com.mirth.connect.model.User;

public class SettingsPanelAdministrator extends AbstractSettingsPanel {

    public static final String TAB_NAME = "Administrator";
    private static Preferences userPreferences;
    private User currentUser = getFrame().getCurrentUser(getFrame());

    public SettingsPanelAdministrator(String tabName) {
        super(tabName);

        initComponents();
    }

    public void doRefresh() {
        if (getFrame().confirmLeave()) {
            dashboardRefreshIntervalField.setDocument(new MirthFieldConstraints(3, false, false, true));
            messageBrowserPageSizeField.setDocument(new MirthFieldConstraints(3, false, false, true));
            eventBrowserPageSizeField.setDocument(new MirthFieldConstraints(3, false, false, true));
            userPreferences = Preferences.userNodeForPackage(Mirth.class);
            int interval = userPreferences.getInt("intervalTime", 10);
            dashboardRefreshIntervalField.setText(interval + "");

            int messageBrowserPageSize = userPreferences.getInt("messageBrowserPageSize", 20);
            messageBrowserPageSizeField.setText(messageBrowserPageSize + "");

            int eventBrowserPageSize = userPreferences.getInt("eventBrowserPageSize", 100);
            eventBrowserPageSizeField.setText(eventBrowserPageSize + "");

            if (userPreferences.getBoolean("messageBrowserFormatXml", true)) {
                formatXmlYesRadio.setSelected(true);
            } else {
                formatXmlNoRadio.setSelected(true);
            }

            if (userPreferences.getBoolean("textSearchWarning", true)) {
                textSearchWarningYesRadio.setSelected(true);
            } else {
                textSearchWarningNoRadio.setSelected(true);
            }
            
            final String workingId = getFrame().startWorking("Loading " + getTabName() + " settings...");
            
            SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
                
                private String checkForNotifications = null;
                
                public Void doInBackground() {
                    try {
                        checkForNotifications = getFrame().mirthClient.getUserPreference(currentUser, "checkForNotifications");
                    } catch (ClientException e) {
                        getFrame().alertException(getFrame(), e.getStackTrace(), e.getMessage());
                    }
                    return null;
                }
                
                @Override
                public void done() {
                    if (checkForNotifications == null || BooleanUtils.toBoolean(checkForNotifications)) {
                        checkForNotificationsYesRadio.setSelected(true);
                    } else {
                        checkForNotificationsNoRadio.setSelected(true);
                    }
                    getFrame().stopWorking(workingId);
                }
            };
            
            worker.execute();
        }
    }

    public boolean doSave() {
        if (dashboardRefreshIntervalField.getText().length() == 0) {
            getFrame().alertWarning(this, "Please enter a valid interval time.");
            return false;
        }
        if (messageBrowserPageSizeField.getText().length() == 0) {
            getFrame().alertWarning(this, "Please enter a valid message browser page size.");
            return false;
        }
        if (eventBrowserPageSizeField.getText().length() == 0) {
            getFrame().alertWarning(this, "Please enter a valid event browser page size.");
            return false;
        }

        int interval = Integer.parseInt(dashboardRefreshIntervalField.getText());
        int messageBrowserPageSize = Integer.parseInt(messageBrowserPageSizeField.getText());
        int eventBrowserPageSize = Integer.parseInt(eventBrowserPageSizeField.getText());

        if (interval <= 0) {
            getFrame().alertWarning(this, "Please enter an interval time that is larger than 0.");
        } else if (messageBrowserPageSize <= 0) {
            getFrame().alertWarning(this, "Please enter an message browser page size larger than 0.");
        } else if (eventBrowserPageSize <= 0) {
            getFrame().alertWarning(this, "Please enter an event browser page size larger than 0.");
        } else {
            userPreferences.putInt("intervalTime", interval);
            userPreferences.putInt("messageBrowserPageSize", messageBrowserPageSize);
            userPreferences.putInt("eventBrowserPageSize", eventBrowserPageSize);
            userPreferences.putBoolean("messageBrowserFormatXml", formatXmlYesRadio.isSelected());
            userPreferences.putBoolean("textSearchWarning", textSearchWarningYesRadio.isSelected());
        }
        final String workingId = getFrame().startWorking("Saving " + getTabName() + " settings...");
        
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            public Void doInBackground() {
                try {
                    getFrame().mirthClient.setUserPreference(currentUser, "checkForNotifications", Boolean.toString(checkForNotificationsYesRadio.isSelected()));
                } catch (ClientException e) {
                    getFrame().alertException(getFrame(), e.getStackTrace(), e.getMessage());
                }
                
                return null;
            }
            
            @Override
            public void done() {
                getFrame().setSaveEnabled(false);
                getFrame().stopWorking(workingId);
            }
        };
        
        worker.execute();
        
        return true;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code
    // <editor-fold defaultstate="collapsed" desc="Generated Code">                          
    private void initComponents() {

        formatXmlButtonGroup = new javax.swing.ButtonGroup();
        textSearchWarningButtonGroup = new javax.swing.ButtonGroup();
        notificationButtonGroup = new javax.swing.ButtonGroup();
        systemSettings = new javax.swing.JPanel();
        dashboardRefreshIntervalLabel = new javax.swing.JLabel();
        dashboardRefreshIntervalField = new com.mirth.connect.client.ui.components.MirthTextField();
        messageBrowserPageSizeField = new com.mirth.connect.client.ui.components.MirthTextField();
        messageBrowserPageSizeLabel = new javax.swing.JLabel();
        formatXmlLabel = new javax.swing.JLabel();
        formatXmlYesRadio = new com.mirth.connect.client.ui.components.MirthRadioButton();
        formatXmlNoRadio = new com.mirth.connect.client.ui.components.MirthRadioButton();
        eventBrowserPageSizeLabel = new javax.swing.JLabel();
        eventBrowserPageSizeField = new com.mirth.connect.client.ui.components.MirthTextField();
        textSearchWarningLabel = new javax.swing.JLabel();
        textSearchWarningYesRadio = new com.mirth.connect.client.ui.components.MirthRadioButton();
        textSearchWarningNoRadio = new com.mirth.connect.client.ui.components.MirthRadioButton();
        userSettings = new javax.swing.JPanel();
        checkForNotificationsLabel = new javax.swing.JLabel();
        checkForNotificationsYesRadio = new com.mirth.connect.client.ui.components.MirthRadioButton();
        checkForNotificationsNoRadio = new com.mirth.connect.client.ui.components.MirthRadioButton();

        setBackground(new java.awt.Color(255, 255, 255));
        setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        systemSettings.setBackground(new java.awt.Color(255, 255, 255));
        systemSettings.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(204, 204, 204)), "System Preferences", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N

        dashboardRefreshIntervalLabel.setText("Dashboard refresh interval (seconds):");

        dashboardRefreshIntervalField.setToolTipText("<html>Interval in seconds at which to refresh the Dashboard. Decrement this for <br>faster updates, and increment it for slower servers with more channels.</html>");

        messageBrowserPageSizeField.setToolTipText("Sets the default page size for browsers (message, event, etc.)");

        messageBrowserPageSizeLabel.setText("Message browser page size:");

        formatXmlLabel.setText("Format XML in message browser:");

        formatXmlYesRadio.setBackground(new java.awt.Color(255, 255, 255));
        formatXmlYesRadio.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        formatXmlButtonGroup.add(formatXmlYesRadio);
        formatXmlYesRadio.setText("Yes");
        formatXmlYesRadio.setToolTipText("Pretty print messages in the message browser that are XML.");
        formatXmlYesRadio.setMargin(new java.awt.Insets(0, 0, 0, 0));

        formatXmlNoRadio.setBackground(new java.awt.Color(255, 255, 255));
        formatXmlNoRadio.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        formatXmlButtonGroup.add(formatXmlNoRadio);
        formatXmlNoRadio.setText("No");
        formatXmlNoRadio.setToolTipText("Pretty print messages in the message browser that are XML.");
        formatXmlNoRadio.setMargin(new java.awt.Insets(0, 0, 0, 0));

        eventBrowserPageSizeLabel.setText("Event browser page size:");

        eventBrowserPageSizeField.setToolTipText("Sets the default page size for browsers (message, event, etc.)");

        textSearchWarningLabel.setText("Message browser text search confirmation:");

        textSearchWarningYesRadio.setBackground(new java.awt.Color(255, 255, 255));
        textSearchWarningYesRadio.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        textSearchWarningButtonGroup.add(textSearchWarningYesRadio);
        textSearchWarningYesRadio.setText("Yes");
        textSearchWarningYesRadio.setToolTipText("<html>Show a confirmation dialog in the message browser when attempting a text search, warning users<br/>that the query may take a long time depending on the amount of messages being searched.</html>");
        textSearchWarningYesRadio.setMargin(new java.awt.Insets(0, 0, 0, 0));

        textSearchWarningNoRadio.setBackground(new java.awt.Color(255, 255, 255));
        textSearchWarningNoRadio.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        textSearchWarningButtonGroup.add(textSearchWarningNoRadio);
        textSearchWarningNoRadio.setText("No");
        textSearchWarningNoRadio.setToolTipText("<html>Show a confirmation dialog in the message browser when attempting a text search, warning users<br/>that the query may take a long time depending on the amount of messages being searched.</html>");
        textSearchWarningNoRadio.setMargin(new java.awt.Insets(0, 0, 0, 0));

        javax.swing.GroupLayout systemSettingsLayout = new javax.swing.GroupLayout(systemSettings);
        systemSettings.setLayout(systemSettingsLayout);
        systemSettingsLayout.setHorizontalGroup(
            systemSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(systemSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(systemSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(textSearchWarningLabel, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(formatXmlLabel, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(eventBrowserPageSizeLabel, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(messageBrowserPageSizeLabel, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(dashboardRefreshIntervalLabel, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(systemSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(dashboardRefreshIntervalField, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(messageBrowserPageSizeField, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(systemSettingsLayout.createSequentialGroup()
                        .addComponent(formatXmlYesRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(formatXmlNoRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(eventBrowserPageSizeField, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(systemSettingsLayout.createSequentialGroup()
                        .addComponent(textSearchWarningYesRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(textSearchWarningNoRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(198, Short.MAX_VALUE))
        );
        systemSettingsLayout.setVerticalGroup(
            systemSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(systemSettingsLayout.createSequentialGroup()
                .addGroup(systemSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(dashboardRefreshIntervalLabel)
                    .addComponent(dashboardRefreshIntervalField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(systemSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(messageBrowserPageSizeLabel)
                    .addComponent(messageBrowserPageSizeField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(systemSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(eventBrowserPageSizeLabel)
                    .addComponent(eventBrowserPageSizeField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(systemSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(formatXmlLabel)
                    .addComponent(formatXmlYesRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(formatXmlNoRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(systemSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(textSearchWarningLabel)
                    .addComponent(textSearchWarningYesRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(textSearchWarningNoRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        userSettings.setBackground(new java.awt.Color(255, 255, 255));
        userSettings.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(204, 204, 204)), "User Preferences", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 1, 11))); // NOI18N

        checkForNotificationsLabel.setText("Check for new notifications on login:");

        checkForNotificationsYesRadio.setBackground(new java.awt.Color(255, 255, 255));
        checkForNotificationsYesRadio.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        notificationButtonGroup.add(checkForNotificationsYesRadio);
        checkForNotificationsYesRadio.setText("Yes");
        checkForNotificationsYesRadio.setToolTipText("<html>Checks for notifications from Mirth (announcements, available updates, etc.)<br/>relevant to this version of Mirth Connect whenever user logs in.</html>");
        checkForNotificationsYesRadio.setMargin(new java.awt.Insets(0, 0, 0, 0));

        checkForNotificationsNoRadio.setBackground(new java.awt.Color(255, 255, 255));
        checkForNotificationsNoRadio.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        notificationButtonGroup.add(checkForNotificationsNoRadio);
        checkForNotificationsNoRadio.setText("No");
        checkForNotificationsNoRadio.setToolTipText("<html>Checks for notifications from Mirth (announcements, available updates, etc.)<br/>relevant to this version of Mirth Connect whenever user logs in.</html>");
        checkForNotificationsNoRadio.setMargin(new java.awt.Insets(0, 0, 0, 0));

        javax.swing.GroupLayout userSettingsLayout = new javax.swing.GroupLayout(userSettings);
        userSettings.setLayout(userSettingsLayout);
        userSettingsLayout.setHorizontalGroup(
            userSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(userSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(checkForNotificationsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(checkForNotificationsYesRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(checkForNotificationsNoRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        userSettingsLayout.setVerticalGroup(
            userSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(userSettingsLayout.createSequentialGroup()
                .addGroup(userSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkForNotificationsYesRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(checkForNotificationsNoRadio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(checkForNotificationsLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(systemSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(userSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(systemSettings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(userSettings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>                        

    // Variables declaration - do not modify                     
    private javax.swing.JLabel checkForNotificationsLabel;
    private com.mirth.connect.client.ui.components.MirthRadioButton checkForNotificationsNoRadio;
    private com.mirth.connect.client.ui.components.MirthRadioButton checkForNotificationsYesRadio;
    private com.mirth.connect.client.ui.components.MirthTextField dashboardRefreshIntervalField;
    private javax.swing.JLabel dashboardRefreshIntervalLabel;
    private com.mirth.connect.client.ui.components.MirthTextField eventBrowserPageSizeField;
    private javax.swing.JLabel eventBrowserPageSizeLabel;
    private javax.swing.ButtonGroup formatXmlButtonGroup;
    private javax.swing.JLabel formatXmlLabel;
    private com.mirth.connect.client.ui.components.MirthRadioButton formatXmlNoRadio;
    private com.mirth.connect.client.ui.components.MirthRadioButton formatXmlYesRadio;
    private com.mirth.connect.client.ui.components.MirthTextField messageBrowserPageSizeField;
    private javax.swing.JLabel messageBrowserPageSizeLabel;
    private javax.swing.ButtonGroup notificationButtonGroup;
    private javax.swing.JPanel systemSettings;
    private javax.swing.ButtonGroup textSearchWarningButtonGroup;
    private javax.swing.JLabel textSearchWarningLabel;
    private com.mirth.connect.client.ui.components.MirthRadioButton textSearchWarningNoRadio;
    private com.mirth.connect.client.ui.components.MirthRadioButton textSearchWarningYesRadio;
    private javax.swing.JPanel userSettings;
    // End of variables declaration                   
}
