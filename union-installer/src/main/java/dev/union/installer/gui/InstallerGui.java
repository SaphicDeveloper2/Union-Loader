package dev.union.installer.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import dev.union.installer.Constants;
import dev.union.installer.client.ClientInstaller;
import dev.union.installer.util.LoaderExtractor;
import dev.union.installer.util.MinecraftDirs;
import dev.union.installer.util.Progress;

/**
 * Swing-based installer GUI. Shows embedded loader version, an editable MC version, a
 * .minecraft directory chooser, a "create launcher profile" toggle, and an Install button.
 * Install runs on a {@link SwingWorker} so the EDT stays responsive.
 */
public final class InstallerGui {
	public static void launch() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException ignored) {
			// fall back to cross-platform L&F
		}

		SwingUtilities.invokeLater(() -> new InstallerGui().show());
	}

	private JFrame frame;
	private JTextField mcVersionField;
	private JTextField mcDirField;
	private JCheckBox createProfileBox;
	private JButton installButton;
	private JLabel statusLabel;
	private JProgressBar progressBar;
	private String loaderVersion;

	private void show() {
		try {
			loaderVersion = LoaderExtractor.readEmbeddedLoaderVersion();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null,
					"Failed to read embedded loader:\n" + e.getMessage(),
					"Union Installer", JOptionPane.ERROR_MESSAGE);
			return;
		}

		frame = new JFrame("Union Installer " + Constants.installerVersion());
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLayout(new BorderLayout(0, 0));

		JPanel form = new JPanel(new GridBagLayout());
		form.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 4, 4, 4);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;

		int row = 0;

		c.gridx = 0; c.gridy = row; c.weightx = 0;
		form.add(new JLabel("Loader version:"), c);
		c.gridx = 1; c.weightx = 1.0;
		form.add(new JLabel(loaderVersion), c);
		row++;

		c.gridx = 0; c.gridy = row; c.weightx = 0;
		form.add(new JLabel("Minecraft version:"), c);
		c.gridx = 1; c.weightx = 1.0;
		mcVersionField = new JTextField("26.1.2", 18);
		form.add(mcVersionField, c);
		row++;

		c.gridx = 0; c.gridy = row; c.weightx = 0;
		form.add(new JLabel(".minecraft directory:"), c);
		JPanel dirPanel = new JPanel(new BorderLayout(4, 0));
		mcDirField = new JTextField(MinecraftDirs.defaultMinecraftDir().toString(), 24);
		dirPanel.add(mcDirField, BorderLayout.CENTER);
		JButton browse = new JButton("Browse…");
		browse.addActionListener(e -> chooseDir());
		dirPanel.add(browse, BorderLayout.EAST);
		c.gridx = 1; c.weightx = 1.0;
		form.add(dirPanel, c);
		row++;

		c.gridx = 1; c.gridy = row;
		createProfileBox = new JCheckBox("Create launcher profile", true);
		form.add(createProfileBox, c);
		row++;

		JPanel statusPanel = new JPanel(new BorderLayout(0, 4));
		statusPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));
		statusLabel = new JLabel(" ");
		progressBar = new JProgressBar();
		progressBar.setIndeterminate(false);
		statusPanel.add(statusLabel, BorderLayout.NORTH);
		statusPanel.add(progressBar, BorderLayout.SOUTH);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
		installButton = new JButton("Install");
		installButton.addActionListener(e -> doInstall());
		JButton quit = new JButton("Close");
		quit.addActionListener(e -> frame.dispose());
		buttons.add(Box.createHorizontalStrut(8));
		buttons.add(quit);
		buttons.add(installButton);

		frame.add(form, BorderLayout.NORTH);
		frame.add(statusPanel, BorderLayout.CENTER);
		frame.add(buttons, BorderLayout.SOUTH);

		frame.pack();
		frame.setMinimumSize(new Dimension(460, frame.getHeight()));
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private void chooseDir() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle("Select .minecraft directory");

		String current = mcDirField.getText();

		if (current != null && !current.isBlank()) {
			File f = new File(current);
			if (f.exists()) chooser.setCurrentDirectory(f);
		}

		int result = chooser.showOpenDialog(frame);

		if (result == JFileChooser.APPROVE_OPTION) {
			mcDirField.setText(chooser.getSelectedFile().getAbsolutePath());
		}
	}

	private void doInstall() {
		String mcVersion = mcVersionField.getText().trim();

		if (mcVersion.isEmpty()) {
			JOptionPane.showMessageDialog(frame, "Please enter a Minecraft version.",
					"Union Installer", JOptionPane.WARNING_MESSAGE);
			return;
		}

		Path mcDir = Paths.get(mcDirField.getText().trim());

		if (!MinecraftDirs.looksLikeMinecraftDir(mcDir)) {
			int choice = JOptionPane.showConfirmDialog(frame,
					mcDir + "\ndoes not look like a .minecraft directory.\nInstall anyway?",
					"Union Installer", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

			if (choice != JOptionPane.YES_OPTION) return;
		}

		boolean addProfile = createProfileBox.isSelected();

		installButton.setEnabled(false);
		progressBar.setIndeterminate(true);
		statusLabel.setText("Starting install…");

		SwingWorker<ClientInstaller.InstallResult, String> worker = new SwingWorker<>() {
			@Override
			protected ClientInstaller.InstallResult doInBackground() throws Exception {
				Progress progress = msg -> publish(msg);
				return ClientInstaller.install(mcDir, mcVersion, addProfile, progress);
			}

			@Override
			protected void process(java.util.List<String> chunks) {
				statusLabel.setText(chunks.get(chunks.size() - 1));
			}

			@Override
			protected void done() {
				progressBar.setIndeterminate(false);
				installButton.setEnabled(true);

				try {
					ClientInstaller.InstallResult r = get();
					statusLabel.setText("Installed " + r.profileName());
					JOptionPane.showMessageDialog(frame,
							"Union " + r.loaderVersion() + " installed for Minecraft " + mcVersion + ".\n\n"
									+ "Profile: " + r.profileName() + "\n"
									+ "Loader JAR: " + r.loaderJar() + "\n\n"
									+ (addProfile
											? "A launcher profile has been added — restart the Minecraft launcher "
											+ "and select it from the dropdown."
											: "Select the profile manually in the launcher."),
							"Union Installer", JOptionPane.INFORMATION_MESSAGE);
				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					statusLabel.setText("Install failed: " + cause.getMessage());
					JOptionPane.showMessageDialog(frame,
							"Install failed:\n" + cause.getMessage(),
							"Union Installer", JOptionPane.ERROR_MESSAGE);
				}
			}
		};

		worker.execute();
	}
}
