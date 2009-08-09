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
 package uk.me.parabola.mkgmap;

 import java.util.Vector;

 import uk.me.parabola.mkgmap.main.Main;

 import org.apache.tools.ant.BuildException;
 import org.apache.tools.ant.Task;
 import org.apache.tools.ant.types.Path;

/**
 * This class provides an ant task for mkgmap.
 * Used like this:
 *  <target name="mkgmap">
     <taskdef name="mkgmap" classname="uk.me.parabola.mkgmap.ant.MKGMapTask"
             classpath="/home/cgawron/mkgmap/dist/mkgmap.jar:."/>
      <mkgmap options="mkgmap.cfg">
       <path>
        <fileset dir="." includes="input_sauerland.osm" />
       </path>
      </mkgmap>
     </target>
 *
 */
public class MKGMapTask extends Task {
    
    private ArgumentProcessor proc = null;
    CommandArgsReader argsReader = null;
    private Vector<Path> paths = new Vector<Path>();

    public void init() {
	proc = new Main();
	argsReader = new CommandArgsReader(proc);
    }

    public void addPath(Path path) {
	paths.add(path);
    }

    public void setOptions(String configFile) {
	argsReader.readConfigFile(configFile);

    }

    public void execute() {
	try {
	    proc.startOptions();
	    for (Path path : paths) {
		String[] includedFiles = path.list();
		for (int i=0; i<includedFiles.length; i++) {
		    String filename = includedFiles[i];
		    log("processing " + filename);
		    argsReader.addOption("input-file", filename);
		}
	    }

	    for (CommandArgsReader.ArgType a : argsReader.getArgList()) {
		a.processArg();
	    }

	    proc.endOptions(new CommandArgs(argsReader.getArgs()));
	}
	catch (Exception ex) {
	    log(ex, 1);
	    ex.printStackTrace();
	    throw new BuildException(ex);
	}

    }


}
 