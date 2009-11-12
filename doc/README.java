$Id$

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

* running

To run, you must have a JRE installed.  See README.invoking and
README.examples.  You can use a downloaded jar or one that you built.
