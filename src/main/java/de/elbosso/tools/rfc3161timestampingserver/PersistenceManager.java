// https://dzone.com/articles/jpa-tutorial-setting-jpa-java
package de.elbosso.tools.rfc3161timestampingserver;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public enum PersistenceManager
{
	INSTANCE;
	private final org.slf4j.Logger CLASS_LOGGER=org.slf4j.LoggerFactory.getLogger(PersistenceManager.class);
	private final static org.slf4j.Logger EXCEPTION_LOGGER=org.slf4j.LoggerFactory.getLogger("ExceptionCatcher");
	private EntityManagerFactory emFactory;
	private PersistenceManager()
	{
		java.lang.String pw= "root";
		String pass = "root";

		java.net.URL url=de.netsysit.util.ResourceLoader.getDockerSecretResource("javax.persistence.jdbc.password_FILE");
		try
		{
			if (url != null)
			{
				java.io.InputStream is = url.openStream();
				pw = de.elbosso.util.Utilities.readIntoString(is).trim();
				is.close();
			}
		}
		catch(java.io.IOException exp)
		{
			CLASS_LOGGER.error(exp.getMessage(),exp);
		}
		java.util.Map<java.lang.String, java.lang.String> emConfig=new java.util.HashMap();
		emConfig.put("javax.persistence.jdbc.url", "jdbc:postgresql://localhost:5432/tsa_db");
        emConfig.put("javax.persistence.jdbc.user", pass);
        emConfig.put("javax.persistence.jdbc.password", pw);
        // "rfc3161timestampingserver" is the value of the name attribute of the persistence-unit element.
		emFactory = Persistence.createEntityManagerFactory("rfc3161timestampingserver",emConfig);
	}
	public EntityManager getEntityManager()
	{
		return emFactory.createEntityManager();
	}
	public void close()
	{
		emFactory.close();
	}
}