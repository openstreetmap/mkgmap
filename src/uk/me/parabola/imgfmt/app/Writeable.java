package uk.me.parabola.imgfmt.app;

/**
 * Interface that can be implemented by objects that write to an ImgFile.
 * @author Thomas Lußnig
 */
public interface Writeable {
	
	public void write(ImgFileWriter writer);
}
