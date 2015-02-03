package fr.inria.hal;

import java.io.File;

/**
 *  @author Patrice Lopez
 */
public class BaseTest {
	
	public File getResourceDir(String resourceDir) throws Exception {
		File file = new File(resourceDir);
		if (!file.exists()) {
			if (!file.mkdirs()) {
				throw new Exception("Cannot start test, because test resource folder is not correctly set.");
			}
		}
		return(file);
	}
	
}