package dev.noctud.latte.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import dev.noctud.latte.config.LatteConfiguration;
import dev.noctud.latte.icons.LatteIcons;
import dev.noctud.latte.settings.LatteSettings;
import dev.noctud.latte.settings.LatteSettingsChangeNotifier;
import dev.noctud.latte.utils.LatteIdeHelper;
import dev.noctud.latte.version.LatteVersion;
import dev.noctud.latte.version.LatteVersionService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class LatteSettingsForm implements Configurable {
    private JPanel panel1;
    private JButton buttonHelp;
    private JLabel logoLabel;
    private JCheckBox enableNetteCheckBox;
    private JCheckBox enableNetteFormsTagsCheckBox;
    private JCheckBox enableLatteTagsAndCheckBox;

    /**
     * The lines the user may force, in the order they are offered. An empty value means
     * auto-detect and is first because it is the answer for almost everybody.
     *
     * Stored as the string itself rather than as the combo's index: the set of supported lines
     * will grow, and an ordinal already written into latte.xml would not survive that.
     */
    private static final List<String> VERSION_CHOICES = List.of("", "2.11", "3.0", "3.1");

    private JComboBox<String> versionCombo;

    private JCheckBox notifyNewerCheckBox;

    private final Project project;
    private boolean changed = false;

    public LatteSettingsForm(Project project) {
        this.project = project;

        logoLabel.setIcon(LatteIcons.LOGO);

        buttonHelp.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                LatteIdeHelper.openUrl(LatteConfiguration.LATTE_HELP_URL + "en/");
            }
        });

        enableNetteCheckBox.setSelected(getSettings().enableNette);
        enableNetteCheckBox.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                LatteSettingsForm.this.changed = true;
            }
        });

        enableNetteFormsTagsCheckBox.setSelected(getSettings().enableNetteForms);
        enableNetteFormsTagsCheckBox.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                LatteSettingsForm.this.changed = true;
            }
        });

        enableLatteTagsAndCheckBox.setEnabled(false);

        panel1.add(buildVersionRow(), BorderLayout.SOUTH);
    }

    /**
     * Built here rather than in the .form file on purpose: that file belongs to the GUI designer,
     * and hand-editing its XML is the kind of change that breaks at runtime rather than at compile
     * time. A control added in code is plain in the diff and cannot desynchronise from a binding.
     */
    private JComponent buildVersionRow() {
        versionCombo = new JComboBox<>();
        for (String choice : VERSION_CHOICES) {
            versionCombo.addItem(labelFor(choice));
        }
        versionCombo.setSelectedIndex(Math.max(0, VERSION_CHOICES.indexOf(getSettings().latteVersionOverride)));
        versionCombo.addActionListener(e -> this.changed = true);

        notifyNewerCheckBox = new JCheckBox("Tell me when the project's Latte is newer than the plugin knows");
        notifyNewerCheckBox.setSelected(getSettings().notifyWhenLatteIsNewerThanKnown);
        notifyNewerCheckBox.addActionListener(e -> this.changed = true);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(new JLabel("Latte version:"));
        row.add(versionCombo);
        row.add(notifyNewerCheckBox);
        return row;
    }

    /**
     * What detection found is written into the auto-detect entry itself, so that the answer the
     * plugin is working from is visible without having to force one to find out.
     */
    private String labelFor(String choice) {
        if (!choice.isEmpty()) {
            return "Latte " + choice;
        }
        LatteVersion detected = LatteVersionService.getInstance(project).getVersion(null);
        return detected.isUndetermined()
            ? "Auto-detect (nothing found)"
            : "Auto-detect (currently: " + detected + ")";
    }

    @Nls
    @Override
    public String getDisplayName() {
        return null;
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        return this.panel1;
    }

    @Override
    public boolean isModified() {
        return this.changed;
    }

    @Override
    public void apply() throws ConfigurationException {
        getSettings().enableNette = enableNetteCheckBox.isSelected();
        getSettings().enableNetteForms = enableNetteFormsTagsCheckBox.isSelected();
        getSettings().latteVersionOverride = VERSION_CHOICES.get(versionCombo.getSelectedIndex());
        getSettings().notifyWhenLatteIsNewerThanKnown = notifyNewerCheckBox.isSelected();

        // Everything on this page decides what the registry answers - which vendors it draws from,
        // and now which Latte version it answers for. A template already open keeps reporting the
        // old errors until it is edited, which reads as the setting not having worked at all. The
        // four custom-definition pages have said so since they were written; this one had not.
        LatteSettingsChangeNotifier.definitionsChanged(this.project);

        this.changed = false;
    }

    private LatteSettings getSettings() {
        return LatteSettings.getInstance(this.project);
    }

    @Override
    public void disposeUIResources() {

    }

}
