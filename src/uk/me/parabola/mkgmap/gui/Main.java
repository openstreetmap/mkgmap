/*
 * Copyright (C) 2007 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 07-Oct-2007
 */
package uk.me.parabola.mkgmap.gui;

import uk.me.parabola.log.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/**
 * Main class for the GUI version of mkgmap.  Sets up the main window.
 *
 * @author Steve Ratcliffe
 */
public class Main extends JFrame {
	private static final Logger log = Logger.getLogger(Main.class);

	private final JMenuBar jMenuBar1 = new JMenuBar();
	private final ResourceBundle resourceBundle =
			ResourceBundle.getBundle("uk/me/parabola/mkgmap/gui/Main");

	public Main() {
		initComponents();
	}

	/**
	 * @param args the command line arguments.
	 */
	public static void main(String[] args) {
		//try {
		//	// Set System L&F
		//	UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		//} catch (Exception e) {
		//	log.debug("could not set system look and feel"); //debug
		//}

		EventQueue.invokeLater(new Runnable() {
			public void run() {
				new Main().setVisible(true);
			}
		});
	}

	private void initComponents() {

		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

		// The file menu
		JMenu fileMenu = new JMenu();
		fileMenu.setText(resourceBundle.getString("menu.file"));

		// The save action
		JMenuItem menuSave = new JMenuItem();
		menuSave.setText(resourceBundle.getString("menu.save"));
		menuSave.setToolTipText(resourceBundle.getString("tooltip.save.the.current.project"));
		menuSave.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				saveState();
			}
		});
		fileMenu.add(menuSave);

		JMenuItem menuExit = new JMenuItem();
		menuExit.setText(resourceBundle.getString("memu.exit"));
		menuExit.setToolTipText(resourceBundle.getString("tooltip.exit"));
		menuExit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}
		});
		fileMenu.add(menuExit);

		jMenuBar1.add(fileMenu);

		setJMenuBar(jMenuBar1);

		LayoutManager layout = new BorderLayout();
		getContentPane().setLayout(layout);

		AppLayout app = new AppLayout();
		Component content = app.getRoot();

		getContentPane().add(content);

		pack();
	}

	private void saveState() {
		System.out.println("saving the state");
	}
}
