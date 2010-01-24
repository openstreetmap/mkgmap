
README.java for mkgmap

* java versions

Version 1.6, or later, of java is required to run mkgmap.

** Mac hints

Mac OS X 10.5 comes with Java 1.5.  You can install 1.6 from
  http://www.apple.com/downloads/macosx/apple/application_updates/javaformacosx105update1.html

After installing, set 1.6 as the default using JAVA preferences, which
will cause 'java -jar foo.jar' to use the 1.6 JRE.  To cause ant to
use 1.6 to compile, set
  export JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Versions/1.6/home

* building

To build, you must have a JDK and apache ant installed.
Then, just run "ant", which will produce dist/mkgmap.jar.

- Installing ant on Windows 95, XP, NT [Barest Basics: PLEASE REVIEW ME]
Download the latest Apache Ant binaries from http://ant.apache.org
Unzip the contents to C:\ANT - at least the bin and lib folders must be directly under c:\ANT
Set the environment variables:
    Start -> Settings -> Control panel -> System -> Advanced -> Environment Variables

    VARIABLE        VALUE (example)             EXPLANATION

    Path            C:\ANT\bin                  Add the path to bin to the other paths already there
    ANT_HOME        C:\ANT                      Create a new variable with the path to your ANT
    JAVA_HOME       C:\Program Files\Java\jdk1.6.0_14   The path to JDK
    CLASSPATH       Should be empty, if it is not run ANT with -noclasspath

Run ANT from the same directory where your build.xml file is located.
    Open a shell (cmd.exe)- navigate to the directory of build.xml, 
    type "ant" or "ant -noclasspath" if CLASSPATH is not empty
    press <ENTER>
If all else fails read the manual (your\path\to\Ant\docs\manual\index.html "Installing Ant" ;-}

- Installing ant on Linux
    Install via your distribution package manager.

* running

To run, you must have a JRE installed.  See README.invoking and
README.examples.  You can use a downloaded jar or one that you built.
