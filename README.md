# AnHALytics

### 1-Harvesting
#### Build/Run
In the main directory(harvestHal):

>mvn clean install

Go in the directory where the jar should exist (target).

The following command displays the help:

>java -jar target/harvestHal-```<current version>```.one-jar.jar -h

For a huge harvesting it's better(and been tested) to use -Xmx2048m to set the JVM memory to avoid OutOfMemoryException.

#### HarvestAll / HarvestDaily
To start harvesting all the documents use :

>java -Xmx2048m -jar target/harvestHal-```<current version>```.one-jar.jar -exe harvestAll

It starts from the current date going through the past dates, here is a samlpe of the request :

>http://api.archives-ouvertes.fr/oai/hal/?verb=ListRecords&metadataPrefix=xml-tei&from=2015-01-14&until=2015-01-14


This command is supposed to run on daily basis :

>java -jar target/harvestHal-```<current version>```.one-jar.jar -exe harvestDaily

#### Storage
We use mongoDb along with gridFS component for file support.
Each 'kind' of files are stored in a collection apart; hal tei => hal-tei-collection , binaries => binaries-collection,..., 

<!-- other additional collections may be added (authors, institutions....) -->



### 2-Indexing
#### Build/Run
In the main directory(indexHal):

>mvn clean install

#### Index HAL collection in ElasticSearch


### 3-Annotate
#### Build/Run
In the main directory(annotateHal):

>mvn clean install

#### Annotate HAL collection


#### Index annotations




