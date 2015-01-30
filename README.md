# AnHALytics

AnHALytics is a project aiming at creating an analytic platform for the [HAL research archive](https://hal.archives-ouvertes.fr), exploring various analytic aspects such as search/discovery, activity and collaboration statistics, trend/technology maps, knowledge and data visualization. The project is supported by an [ADT Inria](http://www.inria.fr/en/research/research-teams/technological-development-at-inria) grant and good will :). 

### 1. Harvesting

#### Grobid installation

The harvester requires [Grobid](https://github.com/grobid/grobid) to be installed. [Grobid](https://github.com/grobid/grobid) is a machine learning library for extracting bibliographical information and full texts from technical and scientific documents, in particular from PDF.

Clone the project from github:
	
	> git clone https://github.com/kermitt2/grobid.git

Update the the sub-project property file ``harvestHal/harvestHal.properties`` with the paths of the Grobid installation.

#### Build

A running instance of MongoDB is required to store the harvested data. Before harvesting, update the sub-project property file ``harvestHal/harvestHal.properties`` with your MongoDB settings. 

In the main directory of the sub-project ``harvestHal``:

	> mvn clean install

An executable jar file is produced under the directory ``harvestHal/target``.

The following command displays the help:

	> java -jar target/harvestHal-``<current version>``.one-jar.jar -h

For a large harvesting task, use -Xmx2048m to set the JVM memory to avoid OutOfMemoryException.


#### HarvestAll / HarvestDaily
To start harvesting all the documents of HAL based on [OAI-PMH](http://www.openarchives.org/pmh) v2, use:

	> java -Xmx2048m -jar target/harvestHal-``<current version>``.one-jar.jar -exe harvestAll

Harvesting is done through a reverse chronological order, here is a sample of the OAI-PMH request:
http://api.archives-ouvertes.fr/oai/hal/?verb=ListRecords&metadataPrefix=xml-tei&from=2015-01-14&until=2015-01-14

To perform an harvesting on a daily basis, use:

> java -jar target/harvestHal-``<current version>``.one-jar.jar -exe harvestDaily

For instance, the process can be configured on a cron table.

#### Storage

We use mongoDb along with gridFS component for file support.
Each type of files are stored in a collection apart; hal tei => hal-tei-collection , binaries => binaries-collection,..., 

<!-- documentation of the collections here -->


### 2. Indexing
#### Build

In the root directory of the sub-project ``indexHal/``:

	>mvn clean install

#### Indexing the HAL collection in ElasticSearch

Before indexing, update the sub-project property file ``indexHal/indexHal.properties`` with your MongoDB and ElasticSearch settings. 

Indexing can be launch with the command: 

	> mvn exec:exec -Pindex

### 3. Annotate
#### Build
In the main directory of the sub-project ``annotateHal/``:

	> mvn clean install

#### Annotation of the HAL collection

The annotation on the HAL collection cab be launch with the command in the main directory of the sub-project ``annotateHal/``:

	> mvn exec:exec -Pannotate

#### Storage of annotations

Annotations are preliminary stored in a MongoDB collection before being indexed in ElasticSearch. 


#### Annotation indexing

For indexing the produced annotations in ElasticSearch, in the main directory of the sub-project ``annotateHal/``:

	> mvn exec:exec -Pindex

### 4. Graph


### 5. Frontends



## License

This code is distributed under [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). 


