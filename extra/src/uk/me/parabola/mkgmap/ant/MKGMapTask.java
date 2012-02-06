 /*
 * Copyright (C) 2009 Christian Gawron
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
 * Author: Christian Gawron
 * Create date: 19-Jun-2009
 */
 package uk.me.parabola.mkgmap.ant;

 import java.util.ArrayList;
 import java.util.List;

 import uk.me.parabola.mkgmap.CommandArgsReader;
 import uk.me.parabola.mkgmap.main.Main;

 import org.apache.tools.ant.BuildException;
 import org.apache.tools.ant.Task;
 import org.apache.tools.ant.types.Path;

/**
 * This class provides an ant task for mkgmap.
 * Used like this:
 *  <target name="mkgmap">
     <taskdef name="mkgmap" classname="uk.me.parabola.mkgmap.ant.MKGMapTask"
             classpath="dist/mkgmap.jar:."/>
      <mkgmap options="mkgmap.cfg">
       <path>
        <fileset dir="maps" includes="*.osm" />
       </path>
      </mkgmap>
     </target>
 *
 */
@SuppressWarnings({"UnusedDeclaration"})
public class MKGMapTask extends Task {

	private final ArrayList<Path> paths = new ArrayList<Path>();
	private String configFile;

	public void addPath(Path path) {
		paths.add(path);
	}

	public void setOptions(String configFile) {
		this.configFile = configFile;
	}

	public void execute() {
		List<String> args = new ArrayList<String>();

		try {
			CommandArgsReader argsReader = new CommandArgsReader(new Main());

			if (configFile != null)
				args.add("--read-config=" + configFile);

			for (Path path : paths) {
				String[] includedFiles = path.list();
				for (String filename : includedFiles) {
					log("processing " + filename);
					args.add("--input-file=" + filename);
				}
			}

			argsReader.readArgs(args.toArray(new String[args.size()]));
		} catch (Exception ex) {
			//log(ex, 1);
			throw new BuildException(ex);
		}
	}
}
 