package de.elbosso.tools.rfc3161timestampingserver;
import ch.qos.logback.classic.Level;
import io.javalin.Javalin;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.influx.InfluxMeterRegistry;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.*;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;

public class App {
	private final static org.slf4j.Logger CLASS_LOGGER=org.slf4j.LoggerFactory.getLogger(App.class);
	private final static org.slf4j.Logger EXCEPTION_LOGGER=org.slf4j.LoggerFactory.getLogger("ExceptionCatcher");

	public static void main(String[] args)
	{
		de.elbosso.util.Utilities.configureBasicStdoutLogging(Level.ALL);
		App.init();
	}
	private static final Javalin init()
	{
		if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug("adding BouncyCastle crypto provider");
		Security.addProvider(new BouncyCastleProvider());
		Javalin app = Javalin.create().start(7000);
		if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug("started app - listening on port 7000");
		app.config.addStaticFiles("/site");
		if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug("added path for static contents: /site (allowed methods: GET)");
		app.get("/chain.pem", ctx -> {
			java.net.URL url=de.netsysit.util.ResourceLoader.getDockerSecretResource("chain.pem");
			if(url==null)
				url=de.netsysit.util.ResourceLoader.getResource("rfc3161timestampingserver/priv/chain.pem");
			java.io.InputStream is=url.openStream();
			java.io.ByteArrayOutputStream baos=new java.io.ByteArrayOutputStream();
			de.elbosso.util.Utilities.copyBetweenStreams(is,baos,true);
			byte[] content=baos.toByteArray();
			ctx.status(201);
			ctx.contentType("application/pkcs7-mime");
			ctx.result(new java.io.ByteArrayInputStream(content));
			Metrics.counter("rfc3161timestampingserver.get", "resourcename","chain.pem","remoteAddr",ctx.req.getRemoteAddr(),"remoteHost",ctx.req.getRemoteHost(),"localAddr",ctx.req.getLocalAddr(),"localName",ctx.req.getLocalName()).increment();
		});
		if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug("added path for cert chain: /chain.pem (allowed methods: GET)");
		app.get("/tsa.crt", ctx -> {
			java.net.URL url=de.netsysit.util.ResourceLoader.getDockerSecretResource("tsa.crt");
			if(url==null)
				url=de.netsysit.util.ResourceLoader.getResource("rfc3161timestampingserver/priv/tsaroot.crt");
			java.io.InputStream is=url.openStream();
			java.io.ByteArrayOutputStream baos=new java.io.ByteArrayOutputStream();
			de.elbosso.util.Utilities.copyBetweenStreams(is,baos,true);
			byte[] content=baos.toByteArray();
			ctx.status(201);
			ctx.contentType("application/pkix-cert");
			ctx.result(new java.io.ByteArrayInputStream(content));
			Metrics.counter("rfc3161timestampingserver.get", "resourcename","tsa.crt","remoteAddr",ctx.req.getRemoteAddr(),"remoteHost",ctx.req.getRemoteHost(),"localAddr",ctx.req.getLocalAddr(),"localName",ctx.req.getLocalName()).increment();
		});

		app.post("/", ctx -> {
			if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug("received timestamp request");
			byte[] tsq=null;
			java.lang.String contentType=ctx.contentType();
			if(ctx.contentType().equals("application/timestamp-query"))
			{
				if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug("timestamp query data in body of request");
				//curl -H "Content-Type: application/timestamp-query" --data-binary '@../../work/expect-dialog-ca.git/_priv/create_ca.sh.tsq' http://localhost:7000/
				if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug("length of timestamp query in body " + ctx.bodyAsBytes().length);
				tsq=ctx.bodyAsBytes();
			}
			else if(ctx.contentType().startsWith("multipart/form-data"))
			{
				if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug("request is multipart/form-data - searching for timestamp query under key\"tsq\"");
				//curl -F "tsq=@../../work/expect-dialog-ca.git/_priv/create_ca.sh.tsq" http://localhost:7000/
				if(ctx.uploadedFile("tsq")!=null)
				{
					if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug("found it - timestamp query length is "+ctx.uploadedFile("tsq").getContentLength());
					java.io.InputStream is=ctx.uploadedFile("tsq").getContent();
					java.io.ByteArrayOutputStream baos=new java.io.ByteArrayOutputStream();
					de.elbosso.util.Utilities.copyBetweenStreams(is,baos,true);
					tsq=baos.toByteArray();
				}
				//curl -F "file=@../../work/expect-dialog-ca.git/_priv/create_ca.sh.tsq" http://localhost:7000/
				else
				{
					if(CLASS_LOGGER.isErrorEnabled())CLASS_LOGGER.error("no field named \"tsq\" found in form data . corrupted request?");
					Metrics.counter("rfc3161timestampingserver.post", "resourcename","/","httpstatus","500","error","tsq not found","contentType",contentType,"remoteAddr",ctx.req.getRemoteAddr(),"remoteHost",ctx.req.getRemoteHost(),"localAddr",ctx.req.getLocalAddr(),"localName",ctx.req.getLocalName()).increment();
				}
			}
			else
			{
				if (CLASS_LOGGER.isDebugEnabled()) CLASS_LOGGER.error("request is not multipart/form-data or application/x-www-form-urlencoded");
				Metrics.counter("rfc3161timestampingserver.post", "resourcename","/","httpstatus","500","error","encoding","contentType",contentType,"remoteAddr",ctx.req.getRemoteAddr(),"remoteHost",ctx.req.getRemoteHost(),"localAddr",ctx.req.getLocalAddr(),"localName",ctx.req.getLocalName()).increment();
			}
			if(tsq!=null)
			{
				EntityManager em = PersistenceManager.INSTANCE.getEntityManager();
				em.getTransaction().begin();
				try
				{
					if (CLASS_LOGGER.isDebugEnabled()) CLASS_LOGGER.debug("timestamp query found");
					CertificateFactory cf = CertificateFactory.getInstance("X.509");

					java.net.URL url = de.netsysit.util.ResourceLoader.getDockerSecretResource("tsa.crt");
					if(url==null)
						url=de.netsysit.util.ResourceLoader.getResource("rfc3161timestampingserver/priv/tsa.crt");
					if (CLASS_LOGGER.isDebugEnabled()) CLASS_LOGGER.debug("Loading TSA cert from " + url);

					java.io.InputStream is = url.openStream();
					X509Certificate rsaSigningCert = (X509Certificate) cf.generateCertificate(is);
					is.close();

					url=de.netsysit.util.ResourceLoader.getDockerSecretResource("tsa.key");
					if(url==null)
						url = de.netsysit.util.ResourceLoader.getResource("rfc3161timestampingserver/priv/tsaroot.key");
					if (CLASS_LOGGER.isDebugEnabled()) CLASS_LOGGER.debug("Loading TSA private key from " + url);
					is = url.openStream();
					java.lang.String privateKeyPEM = de.elbosso.util.Utilities.readIntoString(is, StandardCharsets.UTF_8);
					is.close();

					// strip of header, footer, newlines, whitespaces
					privateKeyPEM = privateKeyPEM
							.replace("-----BEGIN RSA PRIVATE KEY-----", "")
							.replace("-----END RSA PRIVATE KEY-----", "")
							.replaceAll("\\s", "");

					// decode to get the binary DER representation
					byte[] privateKeyDER = Base64.getDecoder().decode(privateKeyPEM);

					KeyFactory keyFactory = KeyFactory.getInstance("RSA");
					PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyDER));
					if (CLASS_LOGGER.isDebugEnabled()) CLASS_LOGGER.debug("Decoding private key successfully finished");

					AlgorithmIdentifier digestAlgorithm = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha384);
					DigestCalculatorProvider digProvider = new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build();
					TimeStampTokenGenerator tsTokenGen = new TimeStampTokenGenerator(
							new JcaSimpleSignerInfoGeneratorBuilder().build("SHA384withRSA", privateKey, rsaSigningCert),
							digProvider.get(digestAlgorithm),
							new ASN1ObjectIdentifier("1.2"));
					if (CLASS_LOGGER.isDebugEnabled())
						CLASS_LOGGER.debug("TimeStampTokenGenerator successfully instantiated");

					tsTokenGen.addCertificates(new JcaCertStore(Collections.singleton(rsaSigningCert)));
					if (CLASS_LOGGER.isDebugEnabled()) CLASS_LOGGER.debug("added certificates");

					TimeStampResponseGenerator tsRespGen = new TimeStampResponseGenerator(tsTokenGen, TSPAlgorithms.ALLOWED);
					if (CLASS_LOGGER.isDebugEnabled())
						CLASS_LOGGER.debug("TimeStampResponseGenerator successfully instantiated");

					TimeStampRequest timeStampRequest = new TimeStampRequest(tsq);

					if (CLASS_LOGGER.isDebugEnabled())
						CLASS_LOGGER.debug("Message imprint: " + timeStampRequest.getMessageImprintAlgOID().getId() + " " + de.elbosso.util.Utilities.formatHexDump(timeStampRequest.getMessageImprintDigest(), true));
					if (CLASS_LOGGER.isDebugEnabled())
						CLASS_LOGGER.debug("Message imprint (Base64): " + timeStampRequest.getMessageImprintAlgOID().getId() + " " + de.elbosso.util.Utilities.base64Encode(timeStampRequest.getMessageImprintDigest()));

					Rfc3161Timestamp rfc3161Timestamp=new Rfc3161Timestamp();
					rfc3161Timestamp.setCreationDate(new java.util.Date());
					rfc3161Timestamp.setMessageImprintAlgOID(timeStampRequest.getMessageImprintAlgOID().getId());
					rfc3161Timestamp.setMessageImprintDigestBase64(de.elbosso.util.Utilities.base64Encode(timeStampRequest.getMessageImprintDigest()));
					rfc3161Timestamp.setMessageImprintDigestHex(de.elbosso.util.Utilities.formatHexDump(timeStampRequest.getMessageImprintDigest(),false).toUpperCase());
					em.persist(rfc3161Timestamp);

					byte[] tsr = tsRespGen.generate(timeStampRequest, rfc3161Timestamp.getId(), rfc3161Timestamp.getCreationDate()).getEncoded();

					rfc3161Timestamp.setTsrData(tsr);
					em.persist(rfc3161Timestamp);

					if (CLASS_LOGGER.isDebugEnabled())
						CLASS_LOGGER.debug("Timestamp Response created - length: " + tsr.length);

					if (CLASS_LOGGER.isDebugEnabled())
						CLASS_LOGGER.debug("Response (Base64): " + de.elbosso.util.Utilities.base64Encode(tsr));

					ctx.status(201);
					ctx.contentType("application/timestamp-reply");
					ctx.header("Content-Disposition","filename=\"reply.tsr\"");
					ctx.result(new java.io.ByteArrayInputStream(tsr));
					em.getTransaction().commit();
					Metrics.counter("rfc3161timestampingserver.post", "resourcename","/","httpstatus",java.lang.Integer.toString(ctx.status()),"success","true","contentType",contentType,"remoteAddr",ctx.req.getRemoteAddr(),"remoteHost",ctx.req.getRemoteHost(),"localAddr",ctx.req.getLocalAddr(),"localName",ctx.req.getLocalName()).increment();
				}
				catch(java.lang.Throwable t)
				{
					ctx.status(500);
					Metrics.counter("rfc3161timestampingserver.post", "resourcename","/","httpstatus",java.lang.Integer.toString(ctx.status()),"error",(t.getMessage()!=null?t.getMessage():"NPE"),"contentType",contentType,"remoteAddr",ctx.req.getRemoteAddr(),"remoteHost",ctx.req.getRemoteHost(),"localAddr",ctx.req.getLocalAddr(),"localName",ctx.req.getLocalName()).increment();
					em.getTransaction().rollback();
				}
				finally
				{
					em.close();
				}
			}
			else
			{
				ctx.status(500);
			}
		});
		if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug("added path for requesting timestamps: / (allowed methods: POST)");
		app.before(ctx -> {
			if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug(ctx.req.getMethod()+" "+ctx.contentType());
		});
		if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug("added before interceptor");
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			app.stop();
		}));
		if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug("added callback for stopping the application");
		app.events(event -> {
			event.serverStopping(() -> { /* Your code here */ });
			event.serverStopped(() -> {
				if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug("Server stopped");
				PersistenceManager.INSTANCE.close();
				if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug("Persistence manager closed");
			});
			if(CLASS_LOGGER.isDebugEnabled())CLASS_LOGGER.debug("added listener for server stopped event");
		});
		return app;
	}
}
