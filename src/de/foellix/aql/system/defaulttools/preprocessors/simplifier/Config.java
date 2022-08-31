package de.foellix.aql.system.defaulttools.preprocessors.simplifier;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import de.foellix.aql.Log;

public class Config {
	public static final String PROPERTIES_FILE = "simplifyParser.properties";

	public static final String DEX2JAR_PATH = "dex2jarPath";
	public static final String THRESHOLD = "threshold";

	private final Properties properties;

	private static Config instance = new Config();

	private Config() {
		this.properties = new Properties();

		try (InputStream input = new FileInputStream(PROPERTIES_FILE)) {
			this.properties.load(input);
		} catch (final IOException e) {
			Log.warning(
					"Could not find/read " + PROPERTIES_FILE + ". Creating new one now!" + Log.getExceptionAppendix(e));
		}
		init();
	}

	private void init() {
		boolean isNew = true;
		boolean changed = false;
		if (this.properties.getProperty(DEX2JAR_PATH) == null) {
			this.properties.setProperty(DEX2JAR_PATH, "/path/to/dex2jar/d2j-dex2jar.sh/.bat");
			changed = true;
		} else {
			isNew = false;
		}
		if (this.properties.getProperty(THRESHOLD) == null) {
			this.properties.setProperty(THRESHOLD, "70.0");
			changed = true;
		} else {
			isNew = false;
		}
		if (changed) {
			Log.msg("Storing " + (isNew ? "new" : "automatically completed") + " " + PROPERTIES_FILE + " file!",
					Log.NORMAL);
			store();
		}
	}

	public static Config getInstance() {
		return instance;
	}

	public String getProperty(String name) {
		return this.properties.getProperty(name);
	}

	public Properties getProperties() {
		return this.properties;
	}

	public void store() {
		try (OutputStream output = new FileOutputStream(PROPERTIES_FILE)) {
			this.properties.store(output, null);
		} catch (final Exception e) {
			Log.error("Could not write " + PROPERTIES_FILE + ". (" + e.getMessage() + ")");
		}
	}
}