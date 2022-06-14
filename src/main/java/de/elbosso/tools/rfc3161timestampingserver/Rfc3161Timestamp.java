package de.elbosso.tools.rfc3161timestampingserver;

import javax.persistence.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;

@Entity
@Table(name = "rfc3161timestamp")
@NamedQuery(name = "Rfc3161Timestamp.findById", query = "SELECT u FROM Rfc3161Timestamp u WHERE u.id=:Id")
@NamedQuery(name = "Rfc3161Timestamp.findYoungestByMsgImprintBase64", query = "SELECT u FROM Rfc3161Timestamp u WHERE u.messageImprintDigestBase64=:Imprint ORDER BY u.creationDate DESC")
@NamedQuery(name = "Rfc3161Timestamp.findYoungestByMsgDigestAndImprintBase64", query = "SELECT u FROM Rfc3161Timestamp u WHERE u.messageImprintAlgOID=:Alg AND u.messageImprintDigestBase64=:Imprint ORDER BY u.creationDate DESC")
@NamedQuery(name = "Rfc3161Timestamp.findYoungestByMsgImprintHex", query = "SELECT u FROM Rfc3161Timestamp u WHERE u.messageImprintDigestHex=:Imprint ORDER BY u.creationDate DESC")
public class Rfc3161Timestamp extends de.elbosso.util.beans.EventHandlingSupport
{
	@Id
	@GeneratedValue
	private BigInteger id;

	public BigInteger getId()
	{
		return id;
	}

	public void setId(BigInteger id)
	{
		BigInteger old = getId();
		this.id = id;
		send("id", old, getId());
	}

	@Column(name="MESSAGE_IMPRINT_ALG_OID", length=255, nullable=false, unique=false)
	java.lang.String messageImprintAlgOID;

	public String getMessageImprintAlgOID()
	{
		return messageImprintAlgOID;
	}

	public void setMessageImprintAlgOID(String messageImprintAlgOID)
	{
		String old = getMessageImprintAlgOID();
		this.messageImprintAlgOID = messageImprintAlgOID;
		send("messageImprintAlgOID", old, getMessageImprintAlgOID());
	}

	@Column(name="MESSAGE_IMPRINT_DIGEST_BASE64", length=1024, nullable=true, unique=false)
	java.lang.String messageImprintDigestBase64;

	public String getMessageImprintDigestBase64()
	{
		return messageImprintDigestBase64;
	}

	public void setMessageImprintDigestBase64(String messageImprintDigestBase64)
	{
		String old = getMessageImprintDigestBase64();
		this.messageImprintDigestBase64 = messageImprintDigestBase64;
		send("messageImprintDigestBase64", old, getMessageImprintDigestBase64());
	}

	@Column(name="MESSAGE_IMPRINT_DIGEST_HEX", length=1024, nullable=true, unique=false)
	java.lang.String messageImprintDigestHex;

	public String getMessageImprintDigestHex()
	{
		return messageImprintDigestHex;
	}

	public void setMessageImprintDigestHex(String messageImprintDigestHex)
	{
		String old = getMessageImprintDigestHex();
		this.messageImprintDigestHex = messageImprintDigestHex;
		send("messageImprintDigestHex", old, getMessageImprintDigestHex());
	}

	@Column(name="CREATION_DATE")
	@Temporal(TemporalType.TIMESTAMP)
	private java.util.Date creationDate;

	public Date getCreationDate()
	{
		return creationDate;
	}

	public void setCreationDate(Date creationDate)
	{
		Date old = getCreationDate();
		this.creationDate = creationDate;
		send("creationDate", old, getCreationDate());
	}

	@Column(name="TSR_DATA")
	@Lob
	private byte[] tsrData;

	public byte[] getTsrData()
	{
		return tsrData;
	}

	public void setTsrData(byte[] tsrData)
	{
		byte[] old = getTsrData();
		this.tsrData = tsrData;
		send("tsrData", old, getTsrData());
	}
}