# rfc3161timestampingserver

<!---
[![start with why](https://img.shields.io/badge/start%20with-why%3F-brightgreen.svg?style=flat)](http://www.ted.com/talks/simon_sinek_how_great_leaders_inspire_action)
--->
[![GitHub release](https://img.shields.io/github/release/elbosso/rfc3161timestampingserver/all.svg?maxAge=1)](https://GitHub.com/elbosso/rfc3161timestampingserver/releases/)
[![GitHub tag](https://img.shields.io/github/tag/elbosso/rfc3161timestampingserver.svg)](https://GitHub.com/elbosso/rfc3161timestampingserver/tags/)
[![made-with-jasva](https://img.shields.io/badge/Made%20with-Java-9cf)](https://www.java.com)
[![GitHub license](https://img.shields.io/github/license/elbosso/rfc3161timestampingserver.svg)](https://github.com/elbosso/rfc3161timestampingserver/blob/master/LICENSE)
[![GitHub Last update](https://img.shields.io/github/last-commit/elbosso/rfc3161timestampingserver)]()
[![GitHub Forks](https://img.shields.io/github/forks/elbosso/rfc3161timestampingserver)]()
[![GitHub Stars](https://img.shields.io/github/stars/elbosso/rfc3161timestampingserver)]()
[![GitHub issues](https://img.shields.io/github/issues/elbosso/rfc3161timestampingserver.svg)](https://GitHub.com/elbosso/rfc3161timestampingserver/issues/)
[![GitHub issues-closed](https://img.shields.io/github/issues-closed/elbosso/rfc3161timestampingserver.svg)](https://GitHub.com/elbosso/rfc3161timestampingserver/issues?q=is%3Aissue+is%3Aclosed)
[![contributions welcome](https://img.shields.io/badge/contributions-welcome-brightgreen.svg?style=flat)](https://github.com/elbosso/rfc3161timestampingserver/issues)
[![GitHub contributors](https://img.shields.io/github/contributors/elbosso/rfc3161timestampingserver.svg)](https://GitHub.com/elbosso/rfc3161timestampingserver/graphs/contributors/)
[![Github All Releases](https://img.shields.io/github/downloads/elbosso/rfc3161timestampingserver/total.svg)](https://github.com/elbosso/rfc3161timestampingserver)
[![Website elbosso.github.io](https://img.shields.io/website-up-down-green-red/https/elbosso.github.io.svg)](https://elbosso.github.io/)

![rfc3161timestampingserver_logo](src/main/resources/site/rfc3161timestampingserver_logo.png)

## Overview

This project offers a [RFC 3161](https://tools.ietf.org/html/rfc3161) compliant timestamping authority/server - you can build it by issuing

```
mvn compile package
```

and then starting the resulting monolithic jar file by issuing

```
$JAVA_HOME/bin/java -jar target/rfc3161timestampingserver-<version>-jar-with-dependencies.jar
```
*Note, however, that to be fully functional, the server needs cryptographic
material, namely*
 * *a certificate named _tsa.crt_*
 * *a private key matching the certificate named _tsa.key_*
 * *and the certificate chain for the certificate named _chain.pem_*
 
*all inside the directory _src/main/resources/rfc3161timestampingserver/priv_.*

The easiest way to get these files is to use a Certificate Authority
managed by project [expect-dialog-ca](https://github.com/elbosso/expect-dialog-ca).
Another advantage of using this project is that you get a configuration file
_tsa.conf_ for working with timestamps using 
[OpenSSL](https://www.openssl.org/) for free (see below).

Alternatively one could just start the server using maven by  issuing

```
mvn compile exec:java
```

In both cases, the server starts on port 7000 - at the moment
only POST  and GET requests are supported. POSTs accept either a body of mimetype
`application/timestamp-query` consisting as the name hints an timestamping request
or if the mimetype is `multipart/form-data`, the form must contain a file
named _tsq_ again being a timestamp request. In case the request brings with it
a valid timestamp request - it is then answered with a matching timestamp reply.
GET is available to search for timestamp replies for checking the integrity.

At the moment, this is a prototype. It still lacks support for TLS.
Serial numbers are stored in a relational database.

The recommended mode of using this is to use the provided Dockerfile 
and docker-compose.yml file. It already brings a correctly configured postgres
instance with it. It is probably better 
to actually use a proxy solution like traefik (the docker-compose is 
already prepared for this) or similar
solutions so the services are actually accessible with a sound hostname and 
some default port.

## Working with it

The project offers some resources to make it easier working with timestamps:
One of them is available under `http://<host>:<port>/tsa.conf`.
It is a configuration that can be used with 
[OpenSSL](https://www.openssl.org/) to create a certificate
request like so:
```shell script
openssl ts -query -config tsa.conf -cert -sha512 -data <path>/<some_file> -no_nonce -out <request_path>/<request>.tsq
```
This request can be sent using a HTTP POST request as multipart form data
(for example from a file upload form inside a web page):
```shell script
curl -F "tsq=@<request>.tsq" http://<host>:<port>/ ><reply>.tsr
``` 
The file _reply.tsr_ contains the timestamp. Alternatively,
this also works with a POST request containing the timestamp query in 
the body of said request having the correct mime-type:
```shell script
curl -H "Content-Type: application/timestamp-query" --data-binary '@<request>.tsq' http://<host>:<port>/ ><reply>.tsr
```
The content of the timestamp (useful for ascertaining the time and date
for example) can be displayed for example with the help of 
OpenSSL command line tools like so:
```shell script
openssl ts -config tsa.conf -reply -in <reply>.tsr -text
```
To verify the timestamp, OpenSSL can help too:
```shell script
openssl ts -verify -config tsa.conf -queryfile <request>.tsq -in <reply>.tsr -CAfile chain.pem
```

The server offers the possibility to search for a message digest - either
with 

```shell script
curl -F "algoid=x.y.z" -F "msgDigestBase64=<base64encodedDigest>" http://<host>:<port>/query --output <queried>.tsr
```

or without specifying the message digest algorithm for computing it:

```shell script
curl -F "msgDigestBase64=<base64encodedDigest>" http://<host>:<port>/query --output <queried>.tsr
```

Alternatively it is possible to search for a message digest formatted as hexdump
without colons as for example sha512 generates:

```shell script
sha512sum <path>/<some_file>
```

```shell script
curl -F "msgDigestHex=<msgDigestAsHexdump>" http://<host>:<port>/query --output <queried>.tsr
```

This project offers a server that adheres to standards - this way, it
can be used as standin for any solution that needs access to a timestamping
server. One example for that is the Java build tool [Ant](https://ant.apache.org/): 
it has a `signjar`
task that takes an attribute named `tsaurl`. If one sets this parameter to
`http://<host>:<port>/`, the jar file is not only signed but also timestamped.

This can get important when the application the jar belongs to is started after
the signing certificate is expired: Ordinarily, the app would not start anymore
but the timestamp guarantees that the certificate was valid at the time 
the timestamp was created and so the application can be used after expiration of the signing
certificate up to the expiration of the timestamping certificate.

## Web Frontend

The solution offers a simple Web Frontend for creation of timestamps and query of created timestamps as
well as for downloading resources such as the signer certificate or the certificate chain. It is 
reachable via `http://<host>:<port>/` using any web browser. The landing page also gives some hints about working
with the service using curl as given above.

## Java Client

There is a new companion project https://github.com/elbosso/rfc3161client that demonstrates the use of
any RFC 3161 compliant server from within Java applications for creation and verification of timestamps.

## Python Client

There is a library called  [rfc3161ng](https://github.com/trbs/rfc3161ng) for using an RFC 3161 compliant server
for the creation of timestamps as well as for cthe verification of such timestamps. It offers examples of
its usage in its README.md - howeve, there is a [pull request](https://github.com/trbs/rfc3161ng/pull/21) out
because i found that in one particular usage scenario it throws an exception during verification of a timestamp. This scenario is 
the verification of the timestamp using the certificate contained within it, not having it downloaded beforehand. So 
I propose - until the said pull request is merged - to verify a timestamp not as show in the README of the project but to do it like so:

    >>> import rfc3161ng
    >>> rt = rfc3161ng.RemoteTimestamper('http://time.certum.pl', include_tsa_certificate=True, certificate=b"", )
    >>> tst = rt.timestamp(data=b'John Doe')
    >>> rt.check(tst, data=b'John Doe')
    True
    >>> rfc3161ng.get_timestamp(tst)
    datetime.datetime(2017, 8, 31, 15, 42, 58, tzinfo=tzutc())
    
The difference here is to specify that the timestamping server should include the certificate. The certificate during construction of
`RemoteTimestamper` is not left unspecified (the default value for the unspecified parameter `certrificate` being `None`) - this
would result in the mentioned exception. Rather, it is explicitly set to an empty string, suppressing the exception and verifying the
timestamp as intended.

*Note, though, that this library does nothing to actually check the validity of the certificate - this is something that the user has to
do by himself by - for example - implementing a full-fledged PKIX chain verification with revocation checks.*

## Migration between Postgres versions

If you use this solution from scratch, you can just edit the file _docker-compose.yml_ matching your target architecture if you want to use a different
version of the Postgres database - currently versions 12, 13 and 14 are supported.

If you are however already running this solution and want to change the database version - simply changing the file _docker-compose.yml_ matching your target architecture will not help: Postgres changes the layout and format of its data files between major versions - the service will not come up if you do so. However, there is a relatively easy way to overcome this:

You should of course make a backup of your persistent volumen for the database before trying any migration work. To do so, you have to stop the service first. Then you can make a backup and start working on the migration. There are many resources concerning upgrading Postgres in Docker - feel free to compare them with the proceedings laid out here.

Word to the wise: it is always a good idea to have some tests ready that can help you validate that the migration was successful. One such test is of course checking if the service still creates timestamps after the migration is finished. Another strongly recommended test is to try out the search for a timestamp using a known valid hash as key - this should produce the same result before and after the migration.

Now, let us get to the actual steps needed to nigrate to a different major version of Postgres while keeping all data intact:
First, lets create a backup of the database in the version currently used by starting the service again and the executing
```
docker-compose exec rfc3161serialnumberprovider pg_dumpall -U jdbctestuser > backup/dump_13.sql
```

This creates a dump file in subdirectory _backup_ - you can of course change the destination file.
Now, the service is stopped.
Afterwards, the volume is deleted. If you use a directory as volume for Postgres data, you can simply rename it (thus having another backup if needed) and
create one with the same name (and attributes and owner) as the original one.
The file _docker-compose.yml_ matching your target architecture is modified - the version of the docker image for Postgres needs to be changed.
Now, the service ist started again - it now starts Postgres in the version wanted but with no data. To populate the database with the backup, issue the following command:
```
docker exec -i rfc3161serialnumberprovider psql -U jdbctestuser -d jdbctest < backup/dump_13.sql
```

Of course, the name and path of the dump file must be the same as given when the dump file was created.

After this command finishes, you can test the solution and you will find that it works as before and has not lost any data. If something should
go wrong and the tests are not successful - shut down the service and undo any changes in the file _docker-compose.yml_ matching your target architecture,
reactivate the backup of the data volume you made earlier and the service will work as before after restarting.
